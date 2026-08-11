package io.github.rafaeljc.argus.transactions.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings.NetQuantityPoint;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcLedgerHoldingsIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Instant SYMBOL_NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private LedgerHoldings ledgerHoldings;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private SymbolRepository symbolRepository;

    @Autowired
    private UserService userService;

    @BeforeEach
    void seedSymbols() {
        symbolRepository.save(
                new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
        symbolRepository.save(
                new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
    }

    @Test
    void timeline_noTransactions_returnsEmpty() {
        UserId userId = newUser();

        assertThat(ledgerHoldings.timeline(userId, LocalDate.parse("2026-06-30"))).isEmpty();
    }

    @Test
    void timeline_singleBuy_returnsOnePointWithFullQuantity() {
        UserId userId = newUser();
        transactions.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));

        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(userId, LocalDate.parse("2026-06-30"));

        assertThat(timeline).containsExactly(
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-01"), new BigDecimal("10.000000")));
    }

    @Test
    void timeline_buyThenPartialSell_returnsRunningNetQuantity() {
        UserId userId = newUser();
        transactions.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        transactions.save(newTransaction(userId, AAPL, Operation.SELL, "4", LocalDate.parse("2026-06-05")));

        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(userId, LocalDate.parse("2026-06-30"));

        assertThat(timeline).containsExactly(
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-01"), new BigDecimal("10.000000")),
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-05"), new BigDecimal("6.000000")));
    }

    @Test
    void timeline_fullSell_returnsZeroNetQuantityPoint() {
        UserId userId = newUser();
        transactions.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        transactions.save(newTransaction(userId, AAPL, Operation.SELL, "10", LocalDate.parse("2026-06-05")));

        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(userId, LocalDate.parse("2026-06-30"));

        assertThat(timeline).containsExactly(
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-01"), new BigDecimal("10.000000")),
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-05"), BigDecimal.ZERO.setScale(6)));
    }

    @Test
    void timeline_multipleTickers_ordersByTradeDateThenTicker() {
        UserId userId = newUser();
        transactions.save(newTransaction(userId, MSFT, Operation.BUY, "5", LocalDate.parse("2026-06-02")));
        transactions.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-02")));

        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(userId, LocalDate.parse("2026-06-30"));

        assertThat(timeline).extracting(NetQuantityPoint::ticker).containsExactly(AAPL, MSFT);
    }

    @Test
    void timeline_throughBeforeTradeDate_excludesLaterTransactions() {
        UserId userId = newUser();
        transactions.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        transactions.save(newTransaction(userId, AAPL, Operation.BUY, "5", LocalDate.parse("2026-06-10")));

        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(userId, LocalDate.parse("2026-06-05"));

        assertThat(timeline).containsExactly(
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-01"), new BigDecimal("10.000000")));
    }

    @Test
    void timeline_scopedToOwner() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        transactions.save(newTransaction(owner, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        transactions.save(newTransaction(otherUser, AAPL, Operation.BUY, "99", LocalDate.parse("2026-06-01")));

        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(owner, LocalDate.parse("2026-06-30"));

        assertThat(timeline).containsExactly(
                new NetQuantityPoint(AAPL, LocalDate.parse("2026-06-01"), new BigDecimal("10.000000")));
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static Transaction newTransaction(
            UserId userId, Ticker ticker, Operation operation, String quantity, LocalDate tradeDate) {
        Instant now = Instant.parse("2026-06-22T12:00:00Z");
        return new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                ticker,
                operation,
                new Quantity(new BigDecimal(quantity)),
                tradeDate,
                now,
                now);
    }
}
