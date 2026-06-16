package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @Test
    void constructor_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Money(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_scaleExceedsTwo_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.234")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "100,     100.00",
            "12.5,    12.50",
            "12.34,   12.34",
            "-5.00,  -5.00",
            "0,       0.00"
    })
    void constructor_validInput_normalizesToScaleTwo(BigDecimal input, BigDecimal expected) {
        Money m = new Money(input);
        assertThat(m.value().scale()).isEqualTo(2);
        assertThat(m.value()).isEqualByComparingTo(expected);
    }
}
