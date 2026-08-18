package io.github.rafaeljc.argus.portfolio.infrastructure.scheduler;

import io.github.rafaeljc.argus.portfolio.application.SnapshotRebuildWorker;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class SnapshotRebuildScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRebuildScheduler.class);

    private final SnapshotRebuildWorker worker;
    private final String workerId = UUID.randomUUID().toString();

    public SnapshotRebuildScheduler(SnapshotRebuildWorker worker) {
        this.worker = worker;
    }

    @PostConstruct
    void logWorkerId() {
        log.info("snapshot rebuild poller started: workerId={}", workerId);
    }

    @Scheduled(fixedDelayString = "${argus.portfolio.snapshot-rebuild.interval-ms}")
    public void poll() {
        try {
            worker.processPendingBatch();
        } catch (RuntimeException e) {
            log.error("snapshot rebuild poll failed", e);
        }
    }
}
