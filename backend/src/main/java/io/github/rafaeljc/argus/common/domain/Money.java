package io.github.rafaeljc.argus.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal value) {

    private static final int SCALE = 2;

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("Money value must not be null");
        }
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "Money scale must be <= " + SCALE + ", got: " + value.scale());
        }
        value = value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
