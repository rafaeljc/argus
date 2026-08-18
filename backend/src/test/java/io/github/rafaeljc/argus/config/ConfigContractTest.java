package io.github.rafaeljc.argus.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

// Guards the required-configuration surface against drift: every ${VAR} placeholder in
// application*.yaml must be documented in .env.example (and vice versa), env var names must
// follow the naming rule mechanically rather than by convention, and no placeholder may carry an
// inline default. This does not validate that any value is well-formed — that is the job of the
// @ConfigurationProperties records and the framework beans that consume them.
class ConfigContractTest {

    // The one deliberate exception to "env var = property path uppercased": ECS hands over the RDS
    // secret field by field, so these three assemble from ARGUS_DB_HOST/PORT/NAME/USERNAME/PASSWORD
    // instead of SPRING_DATASOURCE_*.
    private static final Set<String> NAMING_RULE_EXCEPTIONS =
            Set.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password");

    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");
    private static final Pattern PLACEHOLDER_WITH_DEFAULT = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*):[^}]*}");
    private static final Pattern ENV_VAR_NAME = Pattern.compile("^ARGUS_[A-Z0-9_]+$");
    // Maven resource filtering (e.g. @project.version@) isn't valid YAML on its own — src/main
    // holds the unfiltered source, so strip these before parsing rather than reading target/classes.
    private static final Pattern MAVEN_FILTER_TOKEN = Pattern.compile("@[A-Za-z0-9_.]+@");

    private static List<PlaceholderRef> placeholders;
    private static List<String> inlineDefaults;
    private static Set<String> envExampleKeys;

    private record PropertyLeaf(String file, String path, String rawValue) {}

    private record PlaceholderRef(String file, String path, String varName) {}

    @BeforeAll
    static void loadConfig() throws IOException {
        List<PropertyLeaf> leaves = new ArrayList<>();
        try (var files = Files.list(Path.of("src/main/resources"))) {
            for (Path file : files.filter(ConfigContractTest::isApplicationYaml).toList()) {
                String content = MAVEN_FILTER_TOKEN.matcher(Files.readString(file)).replaceAll("unfiltered");
                Object root = new Yaml().load(content);
                if (root != null) {
                    flatten(file.getFileName().toString(), "", root, leaves);
                }
            }
        }

        placeholders = new ArrayList<>();
        inlineDefaults = new ArrayList<>();
        for (PropertyLeaf leaf : leaves) {
            Matcher withDefault = PLACEHOLDER_WITH_DEFAULT.matcher(leaf.rawValue());
            while (withDefault.find()) {
                inlineDefaults.add(leaf.file() + ":" + leaf.path() + " -> " + leaf.rawValue());
            }
            Matcher required = REQUIRED_PLACEHOLDER.matcher(leaf.rawValue());
            while (required.find()) {
                placeholders.add(new PlaceholderRef(leaf.file(), leaf.path(), required.group(1)));
            }
        }

        envExampleKeys = readEnvExampleKeys(Path.of(".env.example"));
    }

    @Test
    void everyRequiredPlaceholder_hasMatchingEnvExampleEntry() {
        Set<String> required = placeholders.stream().map(PlaceholderRef::varName).collect(LinkedHashSet::new,
                Set::add, Set::addAll);
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(envExampleKeys);
        assertThat(missing).as("required by application*.yaml but missing from .env.example").isEmpty();
    }

    @Test
    void everyEnvExampleEntry_isReferencedByPlaceholder() {
        Set<String> required = placeholders.stream().map(PlaceholderRef::varName).collect(LinkedHashSet::new,
                Set::add, Set::addAll);
        Set<String> dead = new TreeSet<>(envExampleKeys);
        dead.removeAll(required);
        assertThat(dead).as("documented in .env.example but not required by any application*.yaml placeholder")
                .isEmpty();
    }

    @Test
    void placeholderNames_followTheDerivationRuleFromTheirPropertyPath() {
        List<String> violations = new ArrayList<>();
        for (PlaceholderRef ref : placeholders) {
            if (!ENV_VAR_NAME.matcher(ref.varName()).matches()) {
                violations.add(ref.file() + ":" + ref.path() + " -> " + ref.varName()
                        + " does not match ^ARGUS_[A-Z0-9_]+$");
                continue;
            }
            if (NAMING_RULE_EXCEPTIONS.contains(ref.path())) {
                continue;
            }
            String derived = ref.path().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            if (!derived.equals(ref.varName())) {
                violations.add(ref.file() + ":" + ref.path() + " -> expected " + derived + " but was "
                        + ref.varName());
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void noPlaceholder_carriesAnInlineDefault() {
        assertThat(inlineDefaults).isEmpty();
    }

    private static boolean isApplicationYaml(Path path) {
        return path.getFileName().toString().matches("application.*\\.yaml");
    }

    private static void flatten(String file, String prefix, Object node, List<PropertyLeaf> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                flatten(file, path, entry.getValue(), out);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                flatten(file, prefix, item, out);
            }
        } else if (node != null) {
            out.add(new PropertyLeaf(file, prefix, String.valueOf(node)));
        }
    }

    private static Set<String> readEnvExampleKeys(Path path) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                keys.add(trimmed.substring(0, eq).strip());
            }
        }
        return keys;
    }
}
