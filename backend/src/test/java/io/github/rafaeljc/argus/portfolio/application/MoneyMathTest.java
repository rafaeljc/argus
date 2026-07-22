package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoneyMathTest {

    @Test
    void multiplyHalfEven_exactHalfwayRoundsDownToEvenNeighbor() {
        Money result = MoneyMath.multiplyHalfEven(new BigDecimal("2.005"), new Quantity(new BigDecimal("1")));

        assertThat(result).isEqualTo(new Money(new BigDecimal("2.00")));
    }

    @Test
    void multiplyHalfEven_exactHalfwayRoundsUpToEvenNeighbor() {
        Money result = MoneyMath.multiplyHalfEven(new BigDecimal("2.015"), new Quantity(new BigDecimal("1")));

        assertThat(result).isEqualTo(new Money(new BigDecimal("2.02")));
    }

    @Test
    void multiplyHalfEven_fractionalQuantity_multipliesPriceByQuantity() {
        Money result = MoneyMath.multiplyHalfEven(new BigDecimal("100.00"), new Quantity(new BigDecimal("2.5")));

        assertThat(result).isEqualTo(new Money(new BigDecimal("250.00")));
    }

    @Test
    void sum_multipleParts_addsThemTogether() {
        Money total = MoneyMath.sum(List.of(
                new Money(new BigDecimal("10.00")),
                new Money(new BigDecimal("5.50")),
                new Money(new BigDecimal("0.01"))));

        assertThat(total).isEqualTo(new Money(new BigDecimal("15.51")));
    }

    @Test
    void sum_emptyList_returnsZero() {
        Money total = MoneyMath.sum(List.of());

        assertThat(total).isEqualTo(new Money(BigDecimal.ZERO));
    }
}
