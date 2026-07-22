package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotWriterTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-06-22");
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    @Mock
    private GetActiveHoldings getActiveHoldings;

    @Mock
    private PriceLookup priceLookup;

    @Mock
    private PortfolioSnapshotRepository repository;

    private SnapshotWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SnapshotWriter(getActiveHoldings, priceLookup, repository);
    }

    @Test
    void writeFor_noHoldings_insertsZeroTotalSnapshot() {
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of());

        writer.writeFor(USER_ID, SNAPSHOT_DATE);

        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(repository).insertIfAbsent(captor.capture());
        PortfolioSnapshot snapshot = captor.getValue();
        assertThat(snapshot.userId()).isEqualTo(USER_ID);
        assertThat(snapshot.snapshotDate()).isEqualTo(SNAPSHOT_DATE);
        assertThat(snapshot.totalValue()).isEqualTo(new Money(BigDecimal.ZERO));
    }

    @Test
    void writeFor_missingCloseForOneHeldTicker_writesNoRow() {
        Holding aapl = new Holding(USER_ID, AAPL, new Quantity(new BigDecimal("10")), UPDATED_AT);
        Holding msft = new Holding(USER_ID, MSFT, new Quantity(new BigDecimal("5")), UPDATED_AT);
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(aapl, msft));
        when(priceLookup.closesOn(Set.of(AAPL, MSFT), SNAPSHOT_DATE))
                .thenReturn(Map.of(AAPL, new BigDecimal("150.00")));

        writer.writeFor(USER_ID, SNAPSHOT_DATE);

        verify(repository, never()).insertIfAbsent(any());
    }

    @Test
    void writeFor_allTickersHaveCloses_insertsSummedTotal() {
        Holding aapl = new Holding(USER_ID, AAPL, new Quantity(new BigDecimal("10")), UPDATED_AT);
        Holding msft = new Holding(USER_ID, MSFT, new Quantity(new BigDecimal("2")), UPDATED_AT);
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(aapl, msft));
        when(priceLookup.closesOn(Set.of(AAPL, MSFT), SNAPSHOT_DATE))
                .thenReturn(Map.of(AAPL, new BigDecimal("150.00"), MSFT, new BigDecimal("420.75")));

        writer.writeFor(USER_ID, SNAPSHOT_DATE);

        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(repository).insertIfAbsent(captor.capture());
        PortfolioSnapshot snapshot = captor.getValue();
        assertThat(snapshot.userId()).isEqualTo(USER_ID);
        assertThat(snapshot.snapshotDate()).isEqualTo(SNAPSHOT_DATE);
        // 10 * 150.00 + 2 * 420.75 = 1500.00 + 841.50 = 2341.50
        assertThat(snapshot.totalValue()).isEqualTo(new Money(new BigDecimal("2341.50")));
    }
}
