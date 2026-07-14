package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Read-only price facade for peer modules. closesOn is the portfolio-view hot path:
// one query answers "what did each of these tickers close at on this date?".
public interface PriceLookup {

    record Close(Ticker ticker, LocalDate tradeDate, BigDecimal price) {

        public Close {
            if (ticker == null) {
                throw new IllegalArgumentException("Close ticker must not be null");
            }
            if (tradeDate == null) {
                throw new IllegalArgumentException("Close tradeDate must not be null");
            }
            if (price == null) {
                throw new IllegalArgumentException("Close price must not be null");
            }
        }
    }

    Optional<Close> latestClose(Ticker ticker);

    Optional<BigDecimal> closeOn(Ticker ticker, LocalDate date);

    // Tickers with no close on that date are absent from the returned map (never null-valued).
    Map<Ticker, BigDecimal> closesOn(Set<Ticker> tickers, LocalDate date);
}
