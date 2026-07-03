package io.github.rafaeljc.argus.email.infrastructure.scheduler;

import io.github.rafaeljc.argus.email.application.PollOutboxOnce;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class OutboxPollerScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerScheduler.class);

    private final PollOutboxOnce pollOutboxOnce;
    private final String workerId = UUID.randomUUID().toString();

    public OutboxPollerScheduler(PollOutboxOnce pollOutboxOnce) {
        this.pollOutboxOnce = pollOutboxOnce;
    }

    @PostConstruct
    void logWorkerId() {
        log.info("outbox poller started: workerId={}", workerId);
    }

    @Scheduled(fixedDelayString = "${argus.email.poll.interval-ms:30000}")
    public void poll() {
        try {
            pollOutboxOnce.pollOnce(workerId);
        } catch (RuntimeException e) {
            // Swallow so a single bad run doesn't kill the scheduler thread; next run retries.
            log.error("outbox poll failed", e);
        }
    }
}
