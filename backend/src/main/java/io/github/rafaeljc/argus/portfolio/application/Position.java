package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;

public record Position(
        Ticker ticker,
        Quantity quantity,
        BigDecimal lastClosePrice,
        LocalDate lastCloseDate,
        Money positionValue,
        BigDecimal percentOfPortfolio,
        boolean pricePending,
        boolean priceStale,
        LocalDate staleSince) {

    public Position {
        if (ticker == null) {
            throw new IllegalArgumentException("Position ticker must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Position quantity must not be null");
        }
    }
}
