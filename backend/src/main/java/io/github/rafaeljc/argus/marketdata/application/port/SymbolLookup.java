package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.util.Set;

// Read-only symbol facade for peer modules (portfolio, alerts, eodpipeline).
// Batch methods keep cross-module reads to a single round-trip.
public interface SymbolLookup {

    boolean exists(Ticker ticker);

    boolean isDelisted(Ticker ticker);

    // Returns only the tickers that are both known and delisted; unknown tickers are absent.
    Set<Ticker> delistedAmong(Set<Ticker> tickers);
}
