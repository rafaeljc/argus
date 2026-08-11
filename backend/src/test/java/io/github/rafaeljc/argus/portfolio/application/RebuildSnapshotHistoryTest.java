package io.github.rafaeljc.argus.portfolio.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings.NetQuantityPoint;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RebuildSnapshotHistoryTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final LocalDate FROM = LocalDate.parse("2026-06-01");
    private static final LocalDate DAY2 = LocalDate.parse("2026-06-02");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-03");

    @Mock
    private LedgerHoldings ledgerHoldings;

    @Mock
    private PriceLookup priceLookup;

    @Mock
    private PortfolioSnapshotRepository repository;

    private RebuildSnapshotHistory rebuild;

    @BeforeEach
    void setUp() {
        FixedClock clock = new FixedClock(TODAY.atStartOfDay(ZoneId.of("America/New_York")).toInstant());
        rebuild = new RebuildSnapshotHistory(ledgerHoldings, priceLookup, repository, clock);
    }

    @Test
    void rebuild_emptyLedger_deletesAndInsertsNothing() {
        when(ledgerHoldings.timeline(USER_ID, TODAY)).thenReturn(List.of());

        rebuild.rebuild(USER_ID);

        verify(repository).deleteByUser(USER_ID);
        verify(repository, never()).insertAll(any());
        Mockito.verifyNoInteractions(priceLookup);
    }

    @Test
    void rebuild_singleTickerHeldAcrossRange_insertsValuedRowForEachTradingDay() {
        when(ledgerHoldings.timeline(USER_ID, TODAY))
                .thenReturn(List.of(new NetQuantityPoint(AAPL, FROM, new BigDecimal("10"))));
        when(priceLookup.closesBetween(Set.of(AAPL), FROM, TODAY)).thenReturn(Map.of(
                FROM, Map.of(AAPL, new BigDecimal("100.00")),
                DAY2, Map.of(AAPL, new BigDecimal("101.00")),
                TODAY, Map.of(AAPL, new BigDecimal("102.00"))));

        rebuild.rebuild(USER_ID);

        InOrder order = Mockito.inOrder(repository);
        order.verify(repository).deleteByUser(USER_ID);
        ArgumentCaptor<List<PortfolioSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(repository).insertAll(captor.capture());
        List<PortfolioSnapshot> rows = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(rows).containsExactly(
                new PortfolioSnapshot(USER_ID, FROM, new Money(new BigDecimal("1000.00"))),
                new PortfolioSnapshot(USER_ID, DAY2, new Money(new BigDecimal("1010.00"))),
                new PortfolioSnapshot(USER_ID, TODAY, new Money(new BigDecimal("1020.00"))));
    }

    @Test
    void rebuild_tickerFullySoldMidRange_insertsZeroRowsAfterSale() {
        when(ledgerHoldings.timeline(USER_ID, TODAY)).thenReturn(List.of(
                new NetQuantityPoint(AAPL, FROM, new BigDecimal("10")),
                new NetQuantityPoint(AAPL, DAY2, BigDecimal.ZERO)));
        when(priceLookup.closesBetween(Set.of(AAPL), FROM, TODAY))
                .thenReturn(Map.of(FROM, Map.of(AAPL, new BigDecimal("100.00"))));

        rebuild.rebuild(USER_ID);

        ArgumentCaptor<List<PortfolioSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).containsExactly(
                new PortfolioSnapshot(USER_ID, FROM, new Money(new BigDecimal("1000.00"))),
                new PortfolioSnapshot(USER_ID, DAY2, new Money(BigDecimal.ZERO)),
                new PortfolioSnapshot(USER_ID, TODAY, new Money(BigDecimal.ZERO)));
    }

    @Test
    void rebuild_missingCloseOneDay_skipsThatDayOnly() {
        when(ledgerHoldings.timeline(USER_ID, TODAY))
                .thenReturn(List.of(new NetQuantityPoint(AAPL, FROM, new BigDecimal("10"))));
        when(priceLookup.closesBetween(Set.of(AAPL), FROM, TODAY)).thenReturn(Map.of(
                FROM, Map.of(AAPL, new BigDecimal("100.00")),
                TODAY, Map.of(AAPL, new BigDecimal("102.00"))));

        rebuild.rebuild(USER_ID);

        ArgumentCaptor<List<PortfolioSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).extracting(PortfolioSnapshot::snapshotDate)
                .containsExactly(FROM, TODAY);
    }
}
