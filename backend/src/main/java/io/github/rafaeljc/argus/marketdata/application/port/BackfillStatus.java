package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.util.Set;

// Read-only backfill facade. "Pending" = pending or in_progress — the caller cares whether
// prices are still being fetched, not which specific state the job is in.
public interface BackfillStatus {

    boolean isPending(Ticker ticker);

    // Returns only tickers with an active job; ones with no active job are absent.
    Set<Ticker> pendingAmong(Set<Ticker> tickers);
}
