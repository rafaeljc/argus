package io.github.rafaeljc.argus.portfolio.infrastructure.scheduler;

import io.github.rafaeljc.argus.portfolio.application.RequeueInterruptedSnapshotRebuildJobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public class SnapshotRebuildJobReaper {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRebuildJobReaper.class);

    private final RequeueInterruptedSnapshotRebuildJobs requeueInterruptedSnapshotRebuildJobs;

    public SnapshotRebuildJobReaper(RequeueInterruptedSnapshotRebuildJobs requeueInterruptedSnapshotRebuildJobs) {
        this.requeueInterruptedSnapshotRebuildJobs = requeueInterruptedSnapshotRebuildJobs;
    }

    // Swallows so a failure here never stops the application from coming up; the jobs stay stuck
    // and the next restart retries, which is strictly better than refusing to serve traffic.
    @EventListener(ApplicationReadyEvent.class)
    public void requeueJobsLeftByPreviousProcess() {
        try {
            requeueInterruptedSnapshotRebuildJobs.execute();
        } catch (RuntimeException e) {
            log.error("failed to requeue snapshot rebuild jobs left in progress by a previous process", e);
        }
    }
}
