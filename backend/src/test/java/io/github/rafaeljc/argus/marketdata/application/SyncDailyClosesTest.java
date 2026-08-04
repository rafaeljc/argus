package io.github.rafaeljc.argus.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncDailyClosesTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 22);
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");

    @Mock
    private VendorPriceGateway gateway;

    @Mock
    private PriceHistoryRepository priceHistory;

    private CircuitBreaker breaker;
    private SyncDailyCloses useCase;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        useCase = new SyncDailyCloses(gateway, priceHistory, breaker);
    }

    private PriceHistory close(Ticker ticker) {
        return new PriceHistory(ticker, TRADE_DATE, new BigDecimal("100.00"), true, NOW, NOW);
    }

    @Test
    void sync_emptyTickers_skipsVendorAndUpsertEntirely() {
        int result = useCase.sync(Set.of(), TRADE_DATE);

        assertThat(result).isZero();
        verify(gateway, never()).fetchClosesOn(any(), any());
        verify(priceHistory, never()).upsertBatch(any());
    }

    @Test
    void sync_vendorReturnsCloses_upsertsAndReturnsCount() {
        Set<Ticker> tickers = Set.of(AAPL, MSFT);
        List<PriceHistory> prices = List.of(close(AAPL), close(MSFT));
        when(gateway.fetchClosesOn(tickers, TRADE_DATE)).thenReturn(prices);
        when(priceHistory.upsertBatch(prices)).thenReturn(2);

        int result = useCase.sync(tickers, TRADE_DATE);

        assertThat(result).isEqualTo(2);
        verify(priceHistory).upsertBatch(prices);
    }

    @Test
    void sync_vendorReturnsEmptyList_skipsUpsert() {
        Set<Ticker> tickers = Set.of(AAPL);
        when(gateway.fetchClosesOn(tickers, TRADE_DATE)).thenReturn(List.of());

        int result = useCase.sync(tickers, TRADE_DATE);

        assertThat(result).isZero();
        verify(priceHistory, never()).upsertBatch(any());
    }

    @Test
    void sync_breakerOpen_throwsCallNotPermittedAndTouchesNothing() {
        breaker.transitionToForcedOpenState();
        Set<Ticker> tickers = Set.of(AAPL);

        assertThatThrownBy(() -> useCase.sync(tickers, TRADE_DATE))
                .isInstanceOf(CallNotPermittedException.class);

        verify(priceHistory, never()).upsertBatch(any());
    }

    @Test
    void sync_vendorThrows_propagatesAndTouchesNothing() {
        Set<Ticker> tickers = Set.of(AAPL);
        when(gateway.fetchClosesOn(tickers, TRADE_DATE)).thenThrow(new RuntimeException("vendor 503"));

        assertThatThrownBy(() -> useCase.sync(tickers, TRADE_DATE))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("vendor 503");

        verify(priceHistory, never()).upsertBatch(any());
    }
}
