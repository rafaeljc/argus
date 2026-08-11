package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class SnapshotRebuildIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant SYMBOL_NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TX_NOW = Instant.parse("2026-06-22T12:00:00Z");

    @Autowired
    private RebuildSnapshotHistory rebuildSnapshotHistory;

    @Autowired
    private PortfolioSnapshotRepository snapshots;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private SymbolRepository symbolRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private Clock clock;

    @Test
    void rebuild_thenDeleteOnlyTransaction_secondRebuildRemovesStaleSnapshot() {
        UserId userId = newUser();
        LocalDate today = clock.today();
        symbolRepository.save(
                new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
        priceHistoryRepository.upsertBatch(
                List.of(new PriceHistory(AAPL, today, new BigDecimal("150.00"), true, SYMBOL_NOW, SYMBOL_NOW)));
        Transaction saved = transactions.save(newTransaction(userId, AAPL, "10", today));

        rebuildSnapshotHistory.rebuild(userId);

        List<PortfolioSnapshot> firstPass = snapshots.listByUserAndRange(userId, null, null);
        assertThat(firstPass).containsExactly(
                new PortfolioSnapshot(userId, today, new Money(new BigDecimal("1500.00"))));

        transactions.deleteByIdAndUserId(saved.id(), userId);
        rebuildSnapshotHistory.rebuild(userId);

        List<PortfolioSnapshot> secondPass = snapshots.listByUserAndRange(userId, null, null);
        assertThat(secondPass).isEmpty();
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static Transaction newTransaction(UserId userId, Ticker ticker, String quantity, LocalDate tradeDate) {
        return new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                ticker,
                Operation.BUY,
                new Quantity(new BigDecimal(quantity)),
                tradeDate,
                TX_NOW,
                TX_NOW);
    }
}
