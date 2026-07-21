package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import java.util.Optional;

public interface BackfillJobRepository {

    BackfillJob save(BackfillJob job);

    Optional<BackfillJob> findById(JobId id);

    // At most one active (pending or in_progress) job per ticker exists;
    // enforced at the DB layer by backfill_jobs_ticker_active_uidx.
    Optional<BackfillJob> findActiveByTicker(Ticker ticker);

    // Atomically inserts job unless an active job already exists for its ticker; returns
    // whether the insert happened. Backed by an ON CONFLICT DO NOTHING against
    // backfill_jobs_ticker_active_uidx so a racing caller never throws.
    boolean enqueueIfNoActiveJob(BackfillJob job);
}
