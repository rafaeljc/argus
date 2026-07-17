package io.github.rafaeljc.argus.marketdata.infrastructure.scheduler;

import io.github.rafaeljc.argus.marketdata.application.BackfillWorker;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class BackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackfillScheduler.class);

    private final BackfillWorker worker;
    private final String workerId = UUID.randomUUID().toString();

    public BackfillScheduler(BackfillWorker worker) {
        this.worker = worker;
    }

    @PostConstruct
    void logWorkerId() {
        log.info("backfill poller started: workerId={}", workerId);
    }

    @Scheduled(fixedDelayString = "${argus.marketdata.backfill.interval-ms:60000}")
    public void poll() {
        try {
            worker.processPendingBatch();
        } catch (RuntimeException e) {
            log.error("backfill poll failed", e);
        }
    }
}
