package io.github.rafaeljc.argus.auth.infrastructure.scheduler;

import io.github.rafaeljc.argus.auth.application.SweepExpiredSessionsOnce;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class SessionSweeperScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionSweeperScheduler.class);

    private final SweepExpiredSessionsOnce sweepExpiredSessionsOnce;

    public SessionSweeperScheduler(SweepExpiredSessionsOnce sweepExpiredSessionsOnce) {
        this.sweepExpiredSessionsOnce = sweepExpiredSessionsOnce;
    }

    @PostConstruct
    void logStartup() {
        log.info("session sweeper started");
    }

    @Scheduled(fixedDelayString = "${argus.auth.session.sweep.interval-ms:3600000}")
    public void sweep() {
        try {
            sweepExpiredSessionsOnce.execute();
        } catch (RuntimeException e) {
            // Swallow so a single bad run doesn't kill the scheduler thread; next run retries.
            log.error("session sweep failed", e);
        }
    }
}
