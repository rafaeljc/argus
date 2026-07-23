package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-06-22");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    @Mock
    private SnapshotWriter snapshotWriter;

    @Mock
    private GetSnapshot getSnapshot;

    @Mock
    private GetActiveHoldings getActiveHoldings;

    @Mock
    private GetPortfolio getPortfolio;

    private PortfolioService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(snapshotWriter, getSnapshot, getActiveHoldings, getPortfolio);
    }

    @Test
    void writeSnapshot_delegatesToSnapshotWriter() {
        service.writeSnapshot(USER_ID, SNAPSHOT_DATE);

        verify(snapshotWriter).writeFor(USER_ID, SNAPSHOT_DATE);
    }

    @Test
    void getPortfolioSnapshot_delegatesToGetSnapshotAndReturnsItsResult() {
        PortfolioSnapshot expected = new PortfolioSnapshot(USER_ID, SNAPSHOT_DATE, new Money(new BigDecimal("100.00")));
        when(getSnapshot.at(USER_ID, SNAPSHOT_DATE)).thenReturn(Optional.of(expected));

        Optional<PortfolioSnapshot> result = service.getPortfolioSnapshot(USER_ID, SNAPSHOT_DATE);

        assertThat(result).contains(expected);
        verify(getSnapshot).at(USER_ID, SNAPSHOT_DATE);
    }

    @Test
    void getActivePortfolioHoldings_delegatesToGetActiveHoldingsAndReturnsItsResult() {
        Holding holding = new Holding(USER_ID, AAPL, new Quantity(new BigDecimal("10")), UPDATED_AT);
        when(getActiveHoldings.forUser(USER_ID)).thenReturn(List.of(holding));

        List<Holding> result = service.getActivePortfolioHoldings(USER_ID);

        assertThat(result).containsExactly(holding);
        verify(getActiveHoldings).forUser(USER_ID);
    }

    @Test
    void getPortfolio_delegatesToGetPortfolioAndReturnsItsResult() {
        PortfolioView expected =
                new PortfolioView(SNAPSHOT_DATE, new Money(new BigDecimal("100.00")), false, List.of());
        when(getPortfolio.forUser(USER_ID)).thenReturn(expected);

        PortfolioView result = service.getPortfolio(USER_ID);

        assertThat(result).isEqualTo(expected);
        verify(getPortfolio).forUser(USER_ID);
    }
}
