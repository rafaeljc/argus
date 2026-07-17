package io.github.rafaeljc.argus.marketdata.application.port;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import java.time.Instant;
import java.util.Optional;

public interface BackfillJobClaimer {

    Optional<BackfillJob> claimNextPending(Instant now);

    void markCompleted(JobId id, int priceCount, Instant completedAt);

    void markFailed(JobId id, String errorMessage, Instant completedAt);

    void revertToPending(JobId id);
}
