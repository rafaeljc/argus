package io.github.rafaeljc.argus.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobClaimer;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackfillWorkerTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final LocalDate START = LocalDate.of(2021, 6, 15);
    private static final LocalDate END = LocalDate.of(2026, 6, 15);

    @Mock
    private BackfillJobClaimer claimer;

    @Mock
    private VendorPriceGateway gateway;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    private CircuitBreaker breaker;
    private BackfillWorker worker;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        worker = new BackfillWorker(claimer, gateway, priceHistoryRepository, breaker, new FixedClock(NOW));
    }

    private BackfillJob job() {
        return new BackfillJob(
                new JobId(UUID.randomUUID()), AAPL, new UserId(UUID.randomUUID()), JobStatus.IN_PROGRESS,
                START, END, null, null, NOW, NOW, null);
    }

    private PriceHistory price(LocalDate tradeDate) {
        return new PriceHistory(AAPL, tradeDate, BigDecimal.TEN, false, NOW, NOW);
    }

    @Test
    void processOnePendingJob_breakerOpen_returnsFalseAndDoesNotTouchClaimer() {
        breaker.transitionToForcedOpenState();

        boolean result = worker.processOnePendingJob();

        assertThat(result).isFalse();
        verifyNoInteractions(claimer, gateway, priceHistoryRepository);
    }

    @Test
    void processOnePendingJob_noPendingJob_returnsFalse() {
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.empty());

        boolean result = worker.processOnePendingJob();

        assertThat(result).isFalse();
        verifyNoInteractions(gateway, priceHistoryRepository);
    }

    @Test
    void processOnePendingJob_vendorSucceeds_upsertsPricesAndMarksCompleted() {
        BackfillJob claimed = job();
        List<PriceHistory> prices = List.of(price(START), price(START.plusDays(1)));
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenReturn(prices);

        boolean result = worker.processOnePendingJob();

        assertThat(result).isTrue();
        verify(priceHistoryRepository).upsertBatch(prices);
        verify(claimer).markCompleted(claimed.id(), prices.size(), NOW);
        verify(claimer, never()).markFailed(any(), anyString(), any());
        verify(claimer, never()).revertToPending(any());
    }

    @Test
    void processOnePendingJob_vendorThrows_marksFailedWithMessageAndReturnsTrue() {
        BackfillJob claimed = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenThrow(new RuntimeException("vendor 503"));

        boolean result = worker.processOnePendingJob();

        assertThat(result).isTrue();
        verify(claimer).markFailed(claimed.id(), "vendor 503", NOW);
        verify(priceHistoryRepository, never()).upsertBatch(any());
        verify(claimer, never()).markCompleted(any(), anyInt(), any());
    }

    @Test
    void processOnePendingJob_vendorThrowsWithNullMessage_marksFailedWithExceptionClassName() {
        BackfillJob claimed = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenThrow(new RuntimeException());

        worker.processOnePendingJob();

        verify(claimer).markFailed(claimed.id(), "RuntimeException", NOW);
    }

    @Test
    void processOnePendingJob_breakerTripsAfterFirstFailure_secondCallSkipsClaimEntirely() {
        CircuitBreaker tight = CircuitBreaker.of("tight", CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        BackfillWorker tightWorker =
                new BackfillWorker(claimer, gateway, priceHistoryRepository, tight, new FixedClock(NOW));
        BackfillJob first = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(first));
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenThrow(new IllegalStateException("vendor down"));

        boolean firstResult = tightWorker.processOnePendingJob();
        boolean secondResult = tightWorker.processOnePendingJob();

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
        verify(claimer, times(1)).claimNextPending(NOW);
        verify(claimer).markFailed(first.id(), "vendor down", NOW);
        verify(claimer, never()).revertToPending(any());
        verify(claimer, never()).markCompleted(any(), anyInt(), any());
    }

    @Test
    void processOnePendingJob_breakerDeniesCallForAlreadyClaimedJob_revertsToPendingAndReturnsFalse() {
        BackfillJob claimed = job();
        CircuitBreaker mockBreaker = mock(CircuitBreaker.class);
        CircuitBreaker realBreakerForMessage = CircuitBreaker.of("real", CircuitBreakerConfig.ofDefaults());
        when(mockBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(mockBreaker.executeSupplier(ArgumentMatchers.<Supplier<List<PriceHistory>>>any()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(realBreakerForMessage));
        BackfillWorker workerWithMockBreaker =
                new BackfillWorker(claimer, gateway, priceHistoryRepository, mockBreaker, new FixedClock(NOW));
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));

        boolean result = workerWithMockBreaker.processOnePendingJob();

        assertThat(result).isFalse();
        verify(claimer).revertToPending(claimed.id());
        verify(claimer, never()).markFailed(any(), anyString(), any());
        verify(claimer, never()).markCompleted(any(), anyInt(), any());
    }

    @Test
    void processPendingBatch_multiplePendingJobs_processesAllUntilEmpty() {
        BackfillJob a = job();
        BackfillJob b = job();
        BackfillJob c = job();
        when(claimer.claimNextPending(NOW))
                .thenReturn(Optional.of(a))
                .thenReturn(Optional.of(b))
                .thenReturn(Optional.of(c))
                .thenReturn(Optional.empty());
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenReturn(List.of());

        worker.processPendingBatch();

        verify(claimer).markCompleted(a.id(), 0, NOW);
        verify(claimer).markCompleted(b.id(), 0, NOW);
        verify(claimer).markCompleted(c.id(), 0, NOW);
        verify(claimer, times(4)).claimNextPending(NOW);
    }

    @Test
    void processPendingBatch_breakerTripsPartway_stopsClaimingFurtherJobs() {
        CircuitBreaker tight = CircuitBreaker.of("tight", CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        BackfillWorker tightWorker =
                new BackfillWorker(claimer, gateway, priceHistoryRepository, tight, new FixedClock(NOW));
        BackfillJob first = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(first));
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenThrow(new IllegalStateException("vendor down"));

        tightWorker.processPendingBatch();

        verify(claimer, times(1)).claimNextPending(NOW);
        verify(claimer).markFailed(first.id(), "vendor down", NOW);
        verify(claimer, never()).revertToPending(any());
    }

    @Test
    void processPendingBatch_moreJobsThanCap_stopsAtMaxJobsPerTick() {
        BackfillJob endless = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(endless));
        when(gateway.fetchPriceHistory(AAPL, START, END)).thenReturn(List.of());

        worker.processPendingBatch();

        verify(claimer, times(BackfillWorker.MAX_JOBS_PER_TICK)).claimNextPending(NOW);
    }
}
