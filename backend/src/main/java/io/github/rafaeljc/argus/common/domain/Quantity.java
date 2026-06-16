package io.github.rafaeljc.argus.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Quantity(BigDecimal value) {

    private static final int SCALE = 6;

    public Quantity {
        if (value == null) {
            throw new IllegalArgumentException("Quantity value must not be null");
        }
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "Quantity scale must be <= " + SCALE + ", got: " + value.scale());
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0, got: " + value);
        }
        value = value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
