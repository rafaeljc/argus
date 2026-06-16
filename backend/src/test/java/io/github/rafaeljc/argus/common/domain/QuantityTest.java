package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuantityTest {

    @Test
    void constructor_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Quantity(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_scaleExceedsSix_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Quantity(new BigDecimal("1.1234567")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void constructor_nonPositive_throwsIllegalArgument(BigDecimal value) {
        assertThatThrownBy(() -> new Quantity(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "100,        100.000000",
            "1.500,      1.500000",
            "12.345678,  12.345678"
    })
    void constructor_validInput_normalizesToScaleSix(BigDecimal input, BigDecimal expected) {
        Quantity q = new Quantity(input);
        assertThat(q.value().scale()).isEqualTo(6);
        assertThat(q.value()).isEqualByComparingTo(expected);
    }
}
