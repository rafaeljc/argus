package io.github.rafaeljc.argus.portfolio.application.port;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import java.time.Instant;
import java.util.Optional;

public interface SnapshotRebuildJobClaimer {

    Optional<SnapshotRebuildJob> claimNextPending(Instant now);

    void markCompleted(JobId id, Instant completedAt);

    void markFailed(JobId id, String errorMessage, Instant completedAt);
}
