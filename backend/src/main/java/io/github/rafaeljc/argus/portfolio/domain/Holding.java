package io.github.rafaeljc.argus.portfolio.domain;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public record Holding(UserId userId, Ticker ticker, Quantity quantity, Instant updatedAt) {

    public Holding {
        if (userId == null) {
            throw new IllegalArgumentException("Holding userId must not be null");
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Holding ticker must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Holding quantity must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Holding updatedAt must not be null");
        }
    }
}
