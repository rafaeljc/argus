package io.github.rafaeljc.argus.portfolio.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingRebuildTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");

    @Mock
    private HoldingRepository repository;

    private FixedClock clock;
    private HoldingRebuild rebuild;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        rebuild = new HoldingRebuild(repository, clock);
    }

    @Test
    void apply_positiveNetQuantity_upsertsHolding() {
        rebuild.apply(USER_ID, TICKER, new BigDecimal("10"));

        verify(repository).upsert(USER_ID, TICKER, new Quantity(new BigDecimal("10")), FIXED_NOW);
        verify(repository, never()).deleteIfPresent(any(), any());
    }

    @Test
    void apply_zeroNetQuantity_deletesHoldingIfPresent() {
        rebuild.apply(USER_ID, TICKER, BigDecimal.ZERO);

        verify(repository).deleteIfPresent(USER_ID, TICKER);
        verify(repository, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void apply_positiveNetQuantity_doesNotReadCurrentHoldingBeforeUpserting() {
        rebuild.apply(USER_ID, TICKER, new BigDecimal("10"));

        verify(repository, never()).find(any(), any());
    }
}
