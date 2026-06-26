package io.github.rafaeljc.argus.email.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// No class-level @Transactional (unlike EmailService): pollOnce must commit per row so a
// mid-batch crash never rolls back already-published messages. Each repository call's UPDATE
// auto-commits on its own connection.
@Service
public class PollOutboxOnce {

    static final int BATCH_SIZE = 25;

    private static final Logger log = LoggerFactory.getLogger(PollOutboxOnce.class);

    private final OutboxRepository repository;
    private final EmailGateway gateway;
    private final CircuitBreaker breaker;
    private final Clock clock;

    public PollOutboxOnce(
            OutboxRepository repository, EmailGateway gateway, CircuitBreaker breaker, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.breaker = breaker;
        this.clock = clock;
    }

    public void pollOnce(String workerId) {
        CircuitBreaker.State state = breaker.getState();
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            log.info("outbox poll skipped: breaker is {}", state);
            return;
        }
        Instant now = clock.now();
        List<OutboxMessage> batch = repository.claimUnpublishedBatch(BATCH_SIZE, workerId, now);
        for (OutboxMessage msg : batch) {
            if (!processOne(msg)) {
                return;
            }
        }
    }

    private boolean processOne(OutboxMessage msg) {
        try {
            SendResult result = breaker.executeSupplier(() -> gateway.send(msg));
            if (result.success()) {
                repository.markPublished(msg.id(), clock.now());
            } else {
                repository.recordFailure(msg.id(), result.errorMessage());
            }
            return true;
        } catch (CallNotPermittedException e) {
            log.info("outbox poll: breaker opened mid-batch, deferring remaining messages");
            return false;
        } catch (RuntimeException e) {
            String errMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            repository.recordFailure(msg.id(), errMsg);
            log.warn("outbox poll: gateway threw for id={}: {}", msg.id(), errMsg);
            return true;
        }
    }
}
