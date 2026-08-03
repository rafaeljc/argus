package io.github.rafaeljc.argus.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncSymbolUniverseTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");

    @Mock
    private VendorPriceGateway gateway;

    @Mock
    private SymbolRepository symbols;

    private CircuitBreaker breaker;
    private SyncSymbolUniverse useCase;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        useCase = new SyncSymbolUniverse(gateway, symbols, breaker, new FixedClock(NOW));
    }

    private Symbol symbol(Ticker ticker) {
        return new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", false, NOW, NOW, NOW);
    }

    @Test
    void sync_vendorReturnsUniverse_upsertsAndMarksDelistedWithSameInstant() {
        Set<Symbol> universe = Set.of(symbol(AAPL), symbol(MSFT));
        when(gateway.fetchSymbolUniverse()).thenReturn(universe);
        when(symbols.upsertAll(universe, NOW)).thenReturn(2);
        when(symbols.markDelistedIfNotSyncedAt(NOW)).thenReturn(3);

        SymbolSyncResult result = useCase.sync();

        assertThat(result.upserted()).isEqualTo(2);
        assertThat(result.delisted()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(2);
        verify(symbols).upsertAll(universe, NOW);
        verify(symbols).markDelistedIfNotSyncedAt(NOW);
    }

    @Test
    void sync_vendorReturnsEmptyUniverse_skipsUpsertAndDelistingEntirely() {
        when(gateway.fetchSymbolUniverse()).thenReturn(Set.of());

        SymbolSyncResult result = useCase.sync();

        assertThat(result.upserted()).isZero();
        assertThat(result.delisted()).isZero();
        assertThat(result.total()).isZero();
        verify(symbols, never()).upsertAll(any(), any());
        verify(symbols, never()).markDelistedIfNotSyncedAt(any());
    }

    @Test
    void sync_breakerOpen_throwsCallNotPermittedAndTouchesNothing() {
        breaker.transitionToForcedOpenState();

        assertThatThrownBy(() -> useCase.sync()).isInstanceOf(CallNotPermittedException.class);

        verify(symbols, never()).upsertAll(any(), any());
        verify(symbols, never()).markDelistedIfNotSyncedAt(any());
    }

    @Test
    void sync_vendorThrows_propagatesAndTouchesNothing() {
        when(gateway.fetchSymbolUniverse()).thenThrow(new RuntimeException("vendor 503"));

        assertThatThrownBy(() -> useCase.sync())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("vendor 503");

        verify(symbols, never()).upsertAll(any(), eq(NOW));
        verify(symbols, never()).markDelistedIfNotSyncedAt(any());
    }
}
