package io.github.rafaeljc.argus.email.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PollOutboxOnceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final String WORKER_ID = "worker-test";

    @Mock
    private OutboxRepository repository;

    @Mock
    private EmailGateway gateway;

    private CircuitBreaker breaker;
    private PollOutboxOnce useCase;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        useCase = new PollOutboxOnce(repository, gateway, breaker, new FixedClock(NOW));
    }

    private OutboxMessage message(int seq) {
        return new OutboxMessage(
                new OutboxId(UuidCreator.getTimeOrderedEpoch()),
                UuidCreator.getTimeOrderedEpoch(),
                EventType.VERIFICATION,
                "{\"seq\":%d}".formatted(seq),
                "verification:%d".formatted(seq),
                NOW.minusSeconds(10), null, 0, null, null);
    }

    @Test
    void pollOnce_breakerForcedOpen_skipsClaimAndDoesNotTouchGateway() {
        breaker.transitionToForcedOpenState();

        useCase.pollOnce(WORKER_ID);

        verifyNoInteractions(repository, gateway);
    }

    @Test
    void pollOnce_emptyBatch_returnsWithoutCallingGateway() {
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of());

        useCase.pollOnce(WORKER_ID);

        verify(repository).claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW));
        verifyNoInteractions(gateway);
    }

    @Test
    void pollOnce_gatewaySuccess_marksPublished() {
        OutboxMessage msg = message(1);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(msg));
        when(gateway.send(msg)).thenReturn(new SendResult(true, null));

        useCase.pollOnce(WORKER_ID);

        verify(repository).markPublished(msg.id(), NOW);
        verify(repository, never()).recordFailure(any(), anyString());
    }

    @Test
    void pollOnce_gatewayReturnsFailure_recordsFailureAndDoesNotMarkPublished() {
        OutboxMessage msg = message(1);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(msg));
        when(gateway.send(msg)).thenReturn(new SendResult(false, "vendor 503"));

        useCase.pollOnce(WORKER_ID);

        verify(repository).recordFailure(msg.id(), "vendor 503");
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    void pollOnce_gatewayThrows_recordsFailureWithExceptionMessageAndContinuesBatch() {
        OutboxMessage bad = message(1);
        OutboxMessage ok = message(2);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(bad, ok));
        when(gateway.send(bad)).thenThrow(new RuntimeException("connection reset"));
        when(gateway.send(ok)).thenReturn(new SendResult(true, null));

        useCase.pollOnce(WORKER_ID);

        verify(repository).recordFailure(bad.id(), "connection reset");
        verify(repository).markPublished(ok.id(), NOW);
    }

    @Test
    void pollOnce_gatewayThrowsWithNullMessage_recordsFailureWithExceptionClassName() {
        OutboxMessage msg = message(1);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(msg));
        when(gateway.send(msg)).thenThrow(new RuntimeException());

        useCase.pollOnce(WORKER_ID);

        verify(repository).recordFailure(msg.id(), "RuntimeException");
    }

    @Test
    void pollOnce_mixedBatch_processesEachIndependently() {
        OutboxMessage ok = message(1);
        OutboxMessage bad = message(2);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(ok, bad));
        when(gateway.send(ok)).thenReturn(new SendResult(true, null));
        when(gateway.send(bad)).thenReturn(new SendResult(false, "boom"));

        useCase.pollOnce(WORKER_ID);

        verify(repository).markPublished(ok.id(), NOW);
        verify(repository).recordFailure(bad.id(), "boom");
        verify(gateway, times(2)).send(any());
    }

    @Test
    void pollOnce_callNotPermittedAfterFirstFailure_breaksOutOfBatch() {
        CircuitBreaker tight = CircuitBreaker.of("tight", CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        PollOutboxOnce tightUseCase = new PollOutboxOnce(repository, gateway, tight, new FixedClock(NOW));

        OutboxMessage first = message(1);
        OutboxMessage second = message(2);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(first, second));
        when(gateway.send(first)).thenThrow(new IllegalStateException("vendor 503"));

        tightUseCase.pollOnce(WORKER_ID);

        assertThat(tight.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(gateway).send(first);
        verify(gateway, never()).send(second);
        verify(repository).recordFailure(first.id(), "vendor 503");
        verify(repository, never()).recordFailure(eq(second.id()), anyString());
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    void pollOnce_consecutiveGatewayExceptions_tripBreaker() {
        CircuitBreaker tight = CircuitBreaker.of("tight", CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        PollOutboxOnce tightUseCase = new PollOutboxOnce(repository, gateway, tight, new FixedClock(NOW));

        OutboxMessage a = message(1);
        OutboxMessage b = message(2);
        when(repository.claimUnpublishedBatch(anyInt(), eq(WORKER_ID), eq(NOW))).thenReturn(List.of(a, b));
        when(gateway.send(any())).thenThrow(new RuntimeException("vendor down"));

        tightUseCase.pollOnce(WORKER_ID);

        assertThat(tight.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(repository, times(2)).recordFailure(any(), eq("vendor down"));
    }
}
