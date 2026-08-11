package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobClaimer;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SnapshotRebuildWorker {

    static final int MAX_JOBS_PER_TICK = 25;

    private static final Logger log = LoggerFactory.getLogger(SnapshotRebuildWorker.class);

    private final SnapshotRebuildJobClaimer claimer;
    private final RebuildSnapshotHistory rebuildSnapshotHistory;
    private final Clock clock;

    public SnapshotRebuildWorker(
            SnapshotRebuildJobClaimer claimer, RebuildSnapshotHistory rebuildSnapshotHistory, Clock clock) {
        this.claimer = claimer;
        this.rebuildSnapshotHistory = rebuildSnapshotHistory;
        this.clock = clock;
    }

    public void processPendingBatch() {
        for (int i = 0; i < MAX_JOBS_PER_TICK; i++) {
            if (!processOnePendingJob()) {
                return;
            }
        }
    }

    boolean processOnePendingJob() {
        Optional<SnapshotRebuildJob> claimed = claimer.claimNextPending(clock.now());
        if (claimed.isEmpty()) {
            return false;
        }
        return processClaimedJob(claimed.get());
    }

    private boolean processClaimedJob(SnapshotRebuildJob job) {
        try {
            rebuildSnapshotHistory.rebuild(job.userId());
            claimer.markCompleted(job.id(), clock.now());
            return true;
        } catch (RuntimeException e) {
            String msg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            log.warn("snapshot rebuild worker: rebuild failed for job {}: {}", job.id(), msg);
            claimer.markFailed(job.id(), msg, clock.now());
            return true;
        }
    }
}
