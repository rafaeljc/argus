package io.github.rafaeljc.argus.email.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final UUID AGGREGATE_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String PAYLOAD = "{\"to\":\"alice@example.com\"}";
    private static final String IDEMPOTENCE_KEY = "verification:" + AGGREGATE_ID;

    private OutboxRepository repository;
    private EmailService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(OutboxRepository.class);
        service = new EmailService(repository, new FixedClock(NOW));
    }

    @Test
    void enqueue_validInput_persistsMessageWithClockTimestampAndZeroErrors() {
        when(repository.insertIfAbsent(any(OutboxMessage.class))).thenReturn(true);

        service.enqueue(EventType.VERIFICATION, AGGREGATE_ID, PAYLOAD, IDEMPOTENCE_KEY);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).insertIfAbsent(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.id().value()).isNotNull();
        assertThat(saved.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(saved.eventType()).isEqualTo(EventType.VERIFICATION);
        assertThat(saved.payload()).isEqualTo(PAYLOAD);
        assertThat(saved.idempotenceKey()).isEqualTo(IDEMPOTENCE_KEY);
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.publishedAt()).isNull();
        assertThat(saved.errorCount()).isZero();
        assertThat(saved.lastError()).isNull();
        assertThat(saved.publishedByWorkerId()).isNull();
    }

    @Test
    void enqueue_duplicateIdempotenceKey_returnsNormallyWhenRepositoryReportsNoInsert() {
        when(repository.insertIfAbsent(any(OutboxMessage.class))).thenReturn(false);

        assertThatCode(() ->
                service.enqueue(EventType.PASSWORD_RESET, AGGREGATE_ID, PAYLOAD, IDEMPOTENCE_KEY))
                .doesNotThrowAnyException();
    }
}
