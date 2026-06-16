package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PercentageTest {

    @Test
    void constructor_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Percentage(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_scaleExceedsOne_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Percentage(new BigDecimal("1.23")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.4", "100.1"})
    void constructor_outOfRange_throwsIllegalArgument(BigDecimal value) {
        assertThatThrownBy(() -> new Percentage(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "0.5,   0.5",
            "100,   100.0",
            "50,    50.0"
    })
    void constructor_validInput_normalizesToScaleOne(BigDecimal input, BigDecimal expected) {
        Percentage p = new Percentage(input);
        assertThat(p.value().scale()).isEqualTo(1);
        assertThat(p.value()).isEqualByComparingTo(expected);
    }
}
