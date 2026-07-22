package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MoneyMath {

    private static final int MONEY_SCALE = 2;

    private MoneyMath() {}

    public static Money multiplyHalfEven(BigDecimal price, Quantity quantity) {
        return new Money(price.multiply(quantity.value()).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN));
    }

    public static Money sum(List<Money> parts) {
        BigDecimal total = parts.stream().map(Money::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Money(total);
    }
}
