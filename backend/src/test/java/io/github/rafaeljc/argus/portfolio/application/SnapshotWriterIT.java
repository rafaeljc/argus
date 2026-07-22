package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class SnapshotWriterIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate SNAPSHOT_DATE = LocalDate.parse("2026-06-15");

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private UserService userService;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private PriceHistoryRepository prices;

    @Autowired
    private HoldingRepository holdings;

    @Test
    void writeSnapshot_allHeldTickersHaveCloses_persistsSummedTotal() {
        seedSymbol(AAPL);
        seedSymbol(MSFT);
        prices.upsertBatch(List.of(closeOn(AAPL, "150.00"), closeOn(MSFT, "420.75")));
        UserId userId = newUser();
        holdings.upsert(userId, AAPL, new Quantity(new BigDecimal("10")), NOW);
        holdings.upsert(userId, MSFT, new Quantity(new BigDecimal("2")), NOW);

        portfolioService.writeSnapshot(userId, SNAPSHOT_DATE);

        Optional<PortfolioSnapshot> snapshot = portfolioService.getPortfolioSnapshot(userId, SNAPSHOT_DATE);
        assertThat(snapshot).isPresent();
        // 10 * 150.00 + 2 * 420.75 = 2341.50
        assertThat(snapshot.get().totalValue()).isEqualTo(new Money(new BigDecimal("2341.50")));
    }

    @Test
    void writeSnapshot_missingCloseForHeldTicker_writesNoRow() {
        seedSymbol(AAPL);
        seedSymbol(MSFT);
        prices.upsertBatch(List.of(closeOn(AAPL, "150.00")));
        UserId userId = newUser();
        holdings.upsert(userId, AAPL, new Quantity(new BigDecimal("10")), NOW);
        holdings.upsert(userId, MSFT, new Quantity(new BigDecimal("2")), NOW);

        portfolioService.writeSnapshot(userId, SNAPSHOT_DATE);

        assertThat(portfolioService.getPortfolioSnapshot(userId, SNAPSHOT_DATE)).isEmpty();
    }

    @Test
    void writeSnapshot_noHoldings_persistsZeroTotal() {
        UserId userId = newUser();

        portfolioService.writeSnapshot(userId, SNAPSHOT_DATE);

        Optional<PortfolioSnapshot> snapshot = portfolioService.getPortfolioSnapshot(userId, SNAPSHOT_DATE);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().totalValue()).isEqualTo(new Money(BigDecimal.ZERO));
    }

    @Test
    void writeSnapshot_calledTwiceForSameDate_leavesFirstValueUnchanged() {
        UserId userId = newUser();
        seedSymbol(AAPL);
        prices.upsertBatch(List.of(closeOn(AAPL, "150.00")));
        holdings.upsert(userId, AAPL, new Quantity(new BigDecimal("10")), NOW);
        portfolioService.writeSnapshot(userId, SNAPSHOT_DATE);

        // Simulate an orchestrator retry recomputing the same day after holdings changed mid-run.
        holdings.upsert(userId, AAPL, new Quantity(new BigDecimal("999")), NOW);
        portfolioService.writeSnapshot(userId, SNAPSHOT_DATE);

        Optional<PortfolioSnapshot> snapshot = portfolioService.getPortfolioSnapshot(userId, SNAPSHOT_DATE);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().totalValue()).isEqualTo(new Money(new BigDecimal("1500.00")));
    }

    private void seedSymbol(Ticker ticker) {
        symbols.save(new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", false, NOW, NOW, NOW));
    }

    private static PriceHistory closeOn(Ticker ticker, String close) {
        return new PriceHistory(ticker, SNAPSHOT_DATE, new BigDecimal(close), true, NOW, NOW);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }
}
