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
}
