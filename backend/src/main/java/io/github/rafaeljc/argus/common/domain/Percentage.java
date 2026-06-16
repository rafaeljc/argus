package io.github.rafaeljc.argus.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Percentage(BigDecimal value) {

    private static final int SCALE = 1;
    private static final BigDecimal MIN = new BigDecimal("0.5");
    private static final BigDecimal MAX = new BigDecimal("100");

    public Percentage {
        if (value == null) {
            throw new IllegalArgumentException("Percentage value must not be null");
        }
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "Percentage scale must be <= " + SCALE + ", got: " + value.scale());
        }
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException(
                    "Percentage must be in [" + MIN + ", " + MAX + "], got: " + value);
        }
        value = value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
