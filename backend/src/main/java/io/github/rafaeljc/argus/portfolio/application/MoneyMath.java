package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

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

    // Empty when total is zero: a part-of-zero ratio has no finite value (an infinite change from
    // a zero baseline), not an error. Maps directly onto nullable wire fields like
    // percent_of_portfolio.
    public static Optional<BigDecimal> percentOf(Money part, Money total) {
        if (total.value().signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(part.value()
                .multiply(BigDecimal.valueOf(100))
                .divide(total.value(), MONEY_SCALE, RoundingMode.HALF_EVEN));
    }
}
