package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.alerts.domain.Direction;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateAlertRuleRequestTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("5.0");

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_allFieldsValid_noViolations() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, THRESHOLD, 30);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void validate_nullDirection_violatesNotNull() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(null, THRESHOLD, 30);

        assertThat(violatedProperties(request)).contains("direction");
    }

    @Test
    void validate_nullThreshold_violatesNotNull() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, null, 30);

        assertThat(violatedProperties(request)).contains("threshold");
    }

    @Test
    void validate_thresholdBelowMinimum_violatesDecimalMin() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, new BigDecimal("0.4"), 30);

        assertThat(violatedProperties(request)).contains("threshold");
    }

    @Test
    void validate_thresholdAboveMaximum_violatesDecimalMax() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, new BigDecimal("100.1"), 30);

        assertThat(violatedProperties(request)).contains("threshold");
    }

    @Test
    void validate_thresholdWithTooManyFractionDigits_violatesDigits() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, new BigDecimal("5.05"), 30);

        assertThat(violatedProperties(request)).contains("threshold");
    }

    @Test
    void validate_nullWindowDays_violatesNotNull() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, THRESHOLD, null);

        assertThat(violatedProperties(request)).contains("windowDays");
    }

    @Test
    void validate_disallowedWindowDays_violatesLookbackWindowDays() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(Direction.UP, THRESHOLD, 15);

        assertThat(violatedProperties(request)).contains("windowDays");
    }

    private static Set<String> violatedProperties(CreateAlertRuleRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
