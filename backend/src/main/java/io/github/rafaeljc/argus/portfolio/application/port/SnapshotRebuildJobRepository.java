package io.github.rafaeljc.argus.portfolio.application.port;

import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;

public interface SnapshotRebuildJobRepository {

    boolean enqueueIfNoActiveJob(SnapshotRebuildJob job);
}
