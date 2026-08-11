package io.github.rafaeljc.argus.portfolio.domain;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public record SnapshotRebuildJob(JobId id,
                                 UserId userId,
                                 RebuildJobStatus status,
                                 Instant requestedAt,
                                 Instant startedAt,
                                 Instant completedAt,
                                 String errorMessage) {

    public SnapshotRebuildJob {
        if (id == null) {
            throw new IllegalArgumentException("SnapshotRebuildJob id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("SnapshotRebuildJob userId must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("SnapshotRebuildJob status must not be null");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("SnapshotRebuildJob requestedAt must not be null");
        }
        if (startedAt != null && startedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("SnapshotRebuildJob startedAt must not be before requestedAt");
        }
        if (completedAt != null && (startedAt == null || completedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException(
                    "SnapshotRebuildJob completedAt requires startedAt to be set and to be <= completedAt");
        }
    }
}
