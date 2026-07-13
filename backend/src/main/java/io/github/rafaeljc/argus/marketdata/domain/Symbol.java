package io.github.rafaeljc.argus.marketdata.domain;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.time.Instant;

public record Symbol(Ticker ticker,
                     Exchange exchange,
                     String name,
                     boolean isDelisted,
                     Instant lastVendorCheck,
                     Instant createdAt,
                     Instant updatedAt) {

    public Symbol {
        if (ticker == null) {
            throw new IllegalArgumentException("Symbol ticker must not be null");
        }
        if (exchange == null) {
            throw new IllegalArgumentException("Symbol exchange must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Symbol createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Symbol updatedAt must not be null");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Symbol updatedAt must not be before createdAt");
        }
    }
}
