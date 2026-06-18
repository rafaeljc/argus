package io.github.rafaeljc.argus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.SystemClock;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

// One @Test per rule so a failure message names the specific violation.
class ModuleBoundaryTest {

    private static final String ROOT = "io.github.rafaeljc.argus";

    // The Clock invariant is split across two allowlists: ObservabilityConfig is the only
    // class allowed to construct SystemClock; SystemClock is the only class allowed to read
    // system time. A new Clock implementation requires updating both.
    private static final String OBSERVABILITY_CONFIG =
            "io.github.rafaeljc.argus.common.infrastructure.ObservabilityConfig";

    private static final String SYSTEM_CLOCK_FQN =
            "io.github.rafaeljc.argus.common.domain.SystemClock";

    private static final DescribedPredicate<JavaMethodCall> READS_SYSTEM_TIME =
            new DescribedPredicate<>("read system time") {
                @Override
                public boolean test(JavaMethodCall call) {
                    String owner = call.getTargetOwner().getFullName();
                    String name = call.getName();
                    // Production code uses argus.common.domain.Clock; java.time.Clock is
                    // SystemClock's private detail. Ban every call on it (system*, tick*, …)
                    // so new JDK factories are covered without revisiting this predicate.
                    if (owner.equals("java.time.Clock")) {
                        return true;
                    }
                    if (owner.startsWith("java.time.") && name.equals("now")) {
                        return true;
                    }
                    return owner.equals("java.lang.System")
                            && (name.equals("currentTimeMillis") || name.equals("nanoTime"));
                }
            };

    private static final List<String> BUSINESS_MODULES = List.of(
            "users", "auth", "transactions", "portfolio", "marketdata",
            "alerts", "email", "eodpipeline", "admin");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void layerRules_domain_onlyDependsOnCommonAndJdk() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "com.fasterxml.jackson..",
                        "org.slf4j..",
                        "ch.qos.logback..",
                        "net.logstash..")
                .because("domain layer must stay pure: JDK + common only");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void layerRules_application_noWebOrInfra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..web..",
                        "..infrastructure..",
                        "org.springframework.web..",
                        "jakarta.persistence..")
                .because("application orchestrates use cases — never reaches into HTTP or persistence")
                .allowEmptyShould(true);

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void layerRules_web_noInfra() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("web depends on application/domain only — never on adapter internals");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void crossModule_onlyApplicationAndDomain() {
        for (String module : BUSINESS_MODULES) {
            String modulePackage = ROOT + "." + module;
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage(modulePackage + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            modulePackage + ".web..",
                            modulePackage + ".infrastructure..")
                    .because("peer modules may import %s.application + %s.domain only"
                            .formatted(module, module));

            rule.check(PRODUCTION_CLASSES);
        }
    }

    @Test
    void noCycles_amongTopLevelModules() {
        ArchRule rule = slices()
                .matching(ROOT + ".(*)..")
                .should().beFreeOfCycles();

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void common_hasNoModuleDependency() {
        String[] businessPackages = BUSINESS_MODULES.stream()
                .map(m -> ROOT + "." + m + "..")
                .toArray(String[]::new);

        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(businessPackages)
                .because("common is a sink: business modules depend on common, never the other way");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void clock_alwaysInjected_neverNewed() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName(OBSERVABILITY_CONFIG)
                .should().callConstructor(SystemClock.class)
                .orShould().callConstructor(FixedClock.class)
                .because("Clock is injected — ObservabilityConfig owns the only SystemClock construction; "
                        + "FixedClock is a test seam and never wired from production code");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void noSystemOutOrErr() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(PRODUCTION_CLASSES);
    }

    @Test
    void noFieldInjection_atAutowired() {
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(PRODUCTION_CLASSES);
    }

    @Test
    void systemTime_readOnlyBySystemClock() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName(SYSTEM_CLOCK_FQN)
                .should().callMethodWhere(READS_SYSTEM_TIME)
                .orShould().callConstructor(Date.class)
                .because("SystemClock is the single bridge to system time — "
                        + "everywhere else, inject argus.common.domain.Clock");

        rule.check(PRODUCTION_CLASSES);
    }
}
