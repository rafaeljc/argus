package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PriceHistory(Ticker ticker,
                           LocalDate tradeDate,
                           BigDecimal closePrice,
                           boolean isSplitAdjusted,
                           Instant createdAt,
                           Instant updatedAt) {

    public PriceHistory {
        if (ticker == null) {
            throw new IllegalArgumentException("PriceHistory ticker must not be null");
        }
        if (tradeDate == null) {
            throw new IllegalArgumentException("PriceHistory tradeDate must not be null");
        }
        if (closePrice == null) {
            throw new IllegalArgumentException("PriceHistory closePrice must not be null");
        }
        if (closePrice.signum() <= 0) {
            throw new IllegalArgumentException("PriceHistory closePrice must be > 0, got: " + closePrice);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("PriceHistory createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("PriceHistory updatedAt must not be null");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("PriceHistory updatedAt must not be before createdAt");
        }
    }
}
