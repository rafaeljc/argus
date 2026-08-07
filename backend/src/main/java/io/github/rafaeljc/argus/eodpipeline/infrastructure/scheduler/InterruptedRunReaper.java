package io.github.rafaeljc.argus.eodpipeline.infrastructure.scheduler;

import io.github.rafaeljc.argus.eodpipeline.application.FailInterruptedRuns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public class InterruptedRunReaper {

    private static final Logger log = LoggerFactory.getLogger(InterruptedRunReaper.class);

    private final FailInterruptedRuns failInterruptedRuns;

    public InterruptedRunReaper(FailInterruptedRuns failInterruptedRuns) {
        this.failInterruptedRuns = failInterruptedRuns;
    }

    // Swallows so a failure here never stops the application from coming up; the runs stay stuck
    // and the next restart retries, which is strictly better than refusing to serve traffic.
    @EventListener(ApplicationReadyEvent.class)
    public void failRunsLeftByPreviousProcess() {
        try {
            failInterruptedRuns.execute();
        } catch (RuntimeException e) {
            log.error("failed to clear eod pipeline runs left in progress by a previous process", e);
        }
    }
}
