package io.github.rafaeljc.argus.email.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final OutboxRepository repository;
    private final Clock clock;

    public EmailService(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(EventType eventType, UUID aggregateId, String payload, String idempotenceKey) {
        OutboxMessage message = new OutboxMessage(
                new OutboxId(UuidCreator.getTimeOrderedEpoch()),
                aggregateId,
                eventType,
                payload,
                idempotenceKey,
                clock.now(),
                null,
                0,
                null,
                null);
        if (!repository.insertIfAbsent(message)) {
            log.info("outbox enqueue skipped: idempotence_key already present ({})", idempotenceKey);
        }
    }
}
