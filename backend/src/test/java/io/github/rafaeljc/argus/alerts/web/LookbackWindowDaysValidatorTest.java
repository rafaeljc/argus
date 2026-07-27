package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LookbackWindowDaysValidatorTest {

    private final LookbackWindowDaysValidator validator = new LookbackWindowDaysValidator();

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 30, 90, 365, 1095, 1825})
    void isValid_allowedValue_returnsTrue(int days) {
        assertThat(validator.isValid(days, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 15, 400, 1826, -1})
    void isValid_disallowedValue_returnsFalse(int days) {
        assertThat(validator.isValid(days, null)).isFalse();
    }

    @Test
    void isValid_null_returnsTrue() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
