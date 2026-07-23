package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPortfolioTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GE = new Ticker("GE");
    private static final Ticker ZZZZ = new Ticker("ZZZZ");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-22T12:00:00Z");
    private static final LocalDate D1 = LocalDate.parse("2026-06-08");
    private static final LocalDate D2 = LocalDate.parse("2026-06-09");
    private static final LocalDate D3 = LocalDate.parse("2026-06-10");
    private static final FixedClock CLOCK = new FixedClock(Instant.parse("2026-06-15T12:00:00Z"));
    private static final LocalDate TODAY = CLOCK.today();

    @Mock
    private GetActiveHoldings getActiveHoldings;

    @Mock
    private PriceLookup priceLookup;

    @Mock
    private SymbolLookup symbolLookup;

    private GetPortfolio getPortfolio;

    @BeforeEach
    void setUp() {
        getPortfolio = new GetPortfolio(getActiveHoldings, priceLookup, symbolLookup, CLOCK);
    }

    private static Holding holding(Ticker ticker, String quantity) {
        return new Holding(USER_ID, ticker, new Quantity(new BigDecimal(quantity)), UPDATED_AT);
    }

    @Test
    void forUser_noHoldings_returnsEmptyPortfolioAtToday() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of());

        PortfolioView view = getPortfolio.forUser(USER_ID);

        assertThat(view.asOfDate()).isEqualTo(TODAY);
        assertThat(view.totalValue()).isEqualTo(new Money(BigDecimal.ZERO));
        assertThat(view.totalValuePending()).isFalse();
        assertThat(view.positions()).isEmpty();
    }

    @Test
    void forUser_allPriced_returnsPositionsSortedByTickerWithSummedTotal() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(holding(MSFT, "2"), holding(AAPL, "10")));
        when(priceLookup.latestCloses(Set.of(AAPL, MSFT))).thenReturn(Map.of(
                AAPL, new PriceLookup.Close(AAPL, D2, new BigDecimal("150.00")),
                MSFT, new PriceLookup.Close(MSFT, D2, new BigDecimal("420.75"))));
        when(symbolLookup.delistedAmong(Set.of(AAPL, MSFT))).thenReturn(Set.of());

        PortfolioView view = getPortfolio.forUser(USER_ID);

        assertThat(view.asOfDate()).isEqualTo(D2);
        assertThat(view.totalValue()).isEqualTo(new Money(new BigDecimal("2341.50")));
        assertThat(view.totalValuePending()).isFalse();
        assertThat(view.positions()).extracting(p -> p.ticker().value()).containsExactly("AAPL", "MSFT");

        Position aapl = view.positions().get(0);
        assertThat(aapl.positionValue()).isEqualTo(new Money(new BigDecimal("1500.00")));
        assertThat(aapl.percentOfPortfolio()).isEqualByComparingTo("64.06");
        assertThat(aapl.pricePending()).isFalse();
        assertThat(aapl.priceStale()).isFalse();

        Position msft = view.positions().get(1);
        assertThat(msft.positionValue()).isEqualTo(new Money(new BigDecimal("841.50")));
        assertThat(msft.percentOfPortfolio()).isEqualByComparingTo("35.94");
    }

    @Test
    void forUser_tickerWithNoClose_marksPricePendingAndExcludesFromTotal() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(holding(AAPL, "10"), holding(ZZZZ, "5")));
        when(priceLookup.latestCloses(Set.of(AAPL, ZZZZ)))
                .thenReturn(Map.of(AAPL, new PriceLookup.Close(AAPL, D2, new BigDecimal("150.00"))));
        when(symbolLookup.delistedAmong(Set.of(AAPL, ZZZZ))).thenReturn(Set.of());

        PortfolioView view = getPortfolio.forUser(USER_ID);

        assertThat(view.totalValue()).isEqualTo(new Money(new BigDecimal("1500.00")));
        assertThat(view.totalValuePending()).isTrue();

        Position zzzz = view.positions().stream().filter(p -> p.ticker().equals(ZZZZ)).findFirst().orElseThrow();
        assertThat(zzzz.pricePending()).isTrue();
        assertThat(zzzz.lastClosePrice()).isNull();
        assertThat(zzzz.lastCloseDate()).isNull();
        assertThat(zzzz.positionValue()).isNull();
        assertThat(zzzz.percentOfPortfolio()).isNull();
        assertThat(zzzz.priceStale()).isFalse();

        Position aapl = view.positions().stream().filter(p -> p.ticker().equals(AAPL)).findFirst().orElseThrow();
        assertThat(aapl.pricePending()).isFalse();
        assertThat(aapl.positionValue()).isEqualTo(new Money(new BigDecimal("1500.00")));
    }

    @Test
    void forUser_delistedTicker_marksPriceStaleEvenWithCurrentClose() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(holding(GE, "10")));
        when(priceLookup.latestCloses(Set.of(GE)))
                .thenReturn(Map.of(GE, new PriceLookup.Close(GE, D2, new BigDecimal("50.00"))));
        when(symbolLookup.delistedAmong(Set.of(GE))).thenReturn(Set.of(GE));

        PortfolioView view = getPortfolio.forUser(USER_ID);

        Position ge = view.positions().get(0);
        assertThat(ge.priceStale()).isTrue();
        assertThat(ge.staleSince()).isEqualTo(D2);
        assertThat(ge.pricePending()).isFalse();
        assertThat(ge.positionValue()).isEqualTo(new Money(new BigDecimal("500.00")));
        assertThat(view.totalValue()).isEqualTo(new Money(new BigDecimal("500.00")));
        assertThat(view.totalValuePending()).isFalse();
    }

    @Test
    void forUser_closeOlderThanAsOfDate_marksPriceStale() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(holding(AAPL, "10"), holding(GE, "1")));
        when(priceLookup.latestCloses(Set.of(AAPL, GE))).thenReturn(Map.of(
                AAPL, new PriceLookup.Close(AAPL, D3, new BigDecimal("150.00")),
                GE, new PriceLookup.Close(GE, D1, new BigDecimal("50.00"))));
        when(symbolLookup.delistedAmong(Set.of(AAPL, GE))).thenReturn(Set.of());

        PortfolioView view = getPortfolio.forUser(USER_ID);

        assertThat(view.asOfDate()).isEqualTo(D3);
        Position ge = view.positions().stream().filter(p -> p.ticker().equals(GE)).findFirst().orElseThrow();
        assertThat(ge.priceStale()).isTrue();
        assertThat(ge.staleSince()).isEqualTo(D1);
        Position aapl = view.positions().stream().filter(p -> p.ticker().equals(AAPL)).findFirst().orElseThrow();
        assertThat(aapl.priceStale()).isFalse();
    }

    @Test
    void forUser_allPending_asOfDateFallsBackToToday() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(holding(ZZZZ, "5")));
        when(priceLookup.latestCloses(Set.of(ZZZZ))).thenReturn(Map.of());
        when(symbolLookup.delistedAmong(Set.of(ZZZZ))).thenReturn(Set.of());

        PortfolioView view = getPortfolio.forUser(USER_ID);

        assertThat(view.asOfDate()).isEqualTo(TODAY);
    }

    @Test
    void forUser_anyHoldingCount_callsEachPortExactlyOnce() {
        when(getActiveHoldings.forUser(USER_ID))
                .thenReturn(List.of(holding(AAPL, "1"), holding(MSFT, "1"), holding(GE, "1")));
        when(priceLookup.latestCloses(eq(Set.of(AAPL, MSFT, GE)))).thenReturn(Map.of(
                AAPL, new PriceLookup.Close(AAPL, D2, new BigDecimal("150.00")),
                MSFT, new PriceLookup.Close(MSFT, D2, new BigDecimal("420.75")),
                GE, new PriceLookup.Close(GE, D2, new BigDecimal("50.00"))));
        when(symbolLookup.delistedAmong(eq(Set.of(AAPL, MSFT, GE)))).thenReturn(Set.of());

        getPortfolio.forUser(USER_ID);

        verify(getActiveHoldings, times(1)).forUser(USER_ID);
        verify(priceLookup, times(1)).latestCloses(Set.of(AAPL, MSFT, GE));
        verify(symbolLookup, times(1)).delistedAmong(Set.of(AAPL, MSFT, GE));
    }
}
