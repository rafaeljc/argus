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
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import io.github.rafaeljc.argus.users.application.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcTransactionRepositoryIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant SYMBOL_NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TX_CREATED = Instant.parse("2026-06-22T12:00:00Z");

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private SymbolRepository symbolRepository;

    @BeforeEach
    void seedSymbol() {
        symbolRepository.save(
                new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
    }

    @Test
    void save_thenFindByIdAndUserId_returnsPersistedTransaction() {
        UserId userId = newUser();
        Transaction saved = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));

        Optional<Transaction> loaded = repository.findByIdAndUserId(saved.id(), userId);

        assertThat(loaded).isPresent();
        Transaction tx = loaded.get();
        assertThat(tx.id()).isEqualTo(saved.id());
        assertThat(tx.userId()).isEqualTo(userId);
        assertThat(tx.ticker()).isEqualTo(AAPL);
        assertThat(tx.operation()).isEqualTo(Operation.BUY);
        assertThat(tx.quantity()).isEqualTo(new Quantity(new BigDecimal("10")));
        assertThat(tx.tradeDate()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(tx.createdAt()).isEqualTo(TX_CREATED);
        assertThat(tx.updatedAt()).isEqualTo(TX_CREATED);
    }

    @Test
    void findByIdAndUserId_unknownId_returnsEmpty() {
        UserId userId = newUser();
        TransactionId unknown = new TransactionId(UuidCreator.getTimeOrderedEpoch());

        assertThat(repository.findByIdAndUserId(unknown, userId)).isEmpty();
    }

    @Test
    void findByIdAndUserId_differentOwner_returnsEmpty() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        Transaction saved = repository.save(
                newTransaction(owner, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));

        assertThat(repository.findByIdAndUserId(saved.id(), otherUser)).isEmpty();
    }

    @Test
    void deleteByIdAndUserId_ownedTransaction_removesAndReturnsTrue() {
        UserId userId = newUser();
        Transaction saved = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));

        boolean deleted = repository.deleteByIdAndUserId(saved.id(), userId);

        assertThat(deleted).isTrue();
        assertThat(repository.findByIdAndUserId(saved.id(), userId)).isEmpty();
    }

    @Test
    void deleteByIdAndUserId_differentOwner_returnsFalseAndKeepsRow() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        Transaction saved = repository.save(
                newTransaction(owner, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));

        boolean deleted = repository.deleteByIdAndUserId(saved.id(), otherUser);

        assertThat(deleted).isFalse();
        assertThat(repository.findByIdAndUserId(saved.id(), owner)).isPresent();
    }

    @Test
    void deleteByIdAndUserId_unknownId_returnsFalse() {
        UserId userId = newUser();
        TransactionId unknown = new TransactionId(UuidCreator.getTimeOrderedEpoch());

        assertThat(repository.deleteByIdAndUserId(unknown, userId)).isFalse();
    }

    @Test
    void holdingsAsOf_mixedBuyAndSell_returnsSignedSum() {
        UserId userId = newUser();
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-01-01")));
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "5", LocalDate.parse("2026-02-01")));
        repository.save(newTransaction(userId, AAPL, Operation.SELL, "3", LocalDate.parse("2026-03-01")));

        BigDecimal net = repository.holdingsAsOf(userId, AAPL, LocalDate.parse("2026-03-01"));

        assertThat(net).isEqualByComparingTo("12");
    }

    @Test
    void holdingsAsOf_asOfBeforeEarliestTransaction_returnsZero() {
        UserId userId = newUser();
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));

        BigDecimal net = repository.holdingsAsOf(userId, AAPL, LocalDate.parse("2026-01-01"));

        assertThat(net).isEqualByComparingTo("0");
    }

    @Test
    void holdingsAsOf_noRows_returnsZero() {
        UserId userId = newUser();

        BigDecimal net = repository.holdingsAsOf(userId, AAPL, LocalDate.parse("2026-06-01"));

        assertThat(net).isEqualByComparingTo("0");
    }

    @Test
    void holdingsAsOf_fullySold_returnsZero() {
        UserId userId = newUser();
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-01-01")));
        repository.save(newTransaction(userId, AAPL, Operation.SELL, "10", LocalDate.parse("2026-02-01")));

        BigDecimal net = repository.holdingsAsOf(userId, AAPL, LocalDate.parse("2026-02-01"));

        assertThat(net).isEqualByComparingTo("0");
    }

    @Test
    void findAllAfterOrderedByTradeDateThenCreatedAt_returnsBuyAndSellRowsOrderedByTradeDateThenCreatedAt() {
        UserId userId = newUser();
        UserId otherUser = newUser();
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "20", LocalDate.parse("2026-01-01")));
        Transaction laterBuy = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "5", LocalDate.parse("2026-02-01")));
        Transaction laterSell = repository.save(
                newTransaction(userId, AAPL, Operation.SELL, "8", LocalDate.parse("2026-03-01")));
        repository.save(newTransaction(otherUser, AAPL, Operation.SELL, "1", LocalDate.parse("2026-04-01")));

        List<Transaction> after = repository.findAllAfterOrderedByTradeDateThenCreatedAt(
                userId, AAPL, LocalDate.parse("2026-01-01"));

        assertThat(after).extracting(Transaction::id).containsExactly(laterBuy.id(), laterSell.id());
    }

    @Test
    void findAllAfterOrderedByTradeDateThenCreatedAt_noneAfterDate_returnsEmpty() {
        UserId userId = newUser();
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-03-10")));

        assertThat(repository.findAllAfterOrderedByTradeDateThenCreatedAt(userId, AAPL, LocalDate.parse("2026-03-10")))
                .isEmpty();
    }

    @Test
    void listByUserId_ordersByTradeDateDescThenCreatedAtDesc() {
        UserId userId = newUser();
        Transaction oldest = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-01")));
        Transaction newest = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-03-01")));
        Transaction middle = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-02-01")));

        List<Transaction> page = repository.listByUserId(userId, 1, 50);

        assertThat(page).extracting(Transaction::id).containsExactly(newest.id(), middle.id(), oldest.id());
    }

    @Test
    void listByUserId_pagination_slicesCorrectly() {
        UserId userId = newUser();
        Transaction first = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-03-01")));
        Transaction second = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-02-01")));
        Transaction third = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-01")));

        List<Transaction> pageOne = repository.listByUserId(userId, 1, 2);
        List<Transaction> pageTwo = repository.listByUserId(userId, 2, 2);

        assertThat(pageOne).extracting(Transaction::id).containsExactly(first.id(), second.id());
        assertThat(pageTwo).extracting(Transaction::id).containsExactly(third.id());
    }

    @Test
    void listByUserId_scopedToOwner() {
        UserId owner = newUser();
        UserId otherUser = newUser();
        repository.save(newTransaction(owner, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-01")));
        repository.save(newTransaction(otherUser, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-02")));

        assertThat(repository.listByUserId(owner, 1, 50)).hasSize(1);
    }

    @Test
    void countByUserId_matchesNumberOfOwnedTransactions() {
        UserId userId = newUser();
        UserId otherUser = newUser();
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-01")));
        repository.save(newTransaction(userId, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-02")));
        repository.save(newTransaction(otherUser, AAPL, Operation.BUY, "1", LocalDate.parse("2026-01-03")));

        assertThat(repository.countByUserId(userId)).isEqualTo(2);
    }

    @Test
    void countByUserId_noTransactions_returnsZero() {
        UserId userId = newUser();

        assertThat(repository.countByUserId(userId)).isZero();
    }

    @Test
    void update_changesQuantityOnly_keepsOperationTradeDateTickerAndCreatedAt() {
        UserId userId = newUser();
        Transaction saved = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        Instant newUpdatedAt = Instant.parse("2026-06-22T13:00:00Z");
        Transaction proposed = new Transaction(
                saved.id(), userId, saved.ticker(), saved.operation(),
                new Quantity(new BigDecimal("7")), saved.tradeDate(), saved.createdAt(), newUpdatedAt);

        repository.update(proposed);

        Transaction reloaded = repository.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.quantity()).isEqualTo(new Quantity(new BigDecimal("7")));
        assertThat(reloaded.operation()).isEqualTo(saved.operation());
        assertThat(reloaded.tradeDate()).isEqualTo(saved.tradeDate());
        assertThat(reloaded.ticker()).isEqualTo(saved.ticker());
        assertThat(reloaded.createdAt()).isEqualTo(saved.createdAt());
        assertThat(reloaded.updatedAt()).isEqualTo(newUpdatedAt);
    }

    @Test
    void update_changesTradeDateOnly_keepsOperationQuantityTickerAndCreatedAt() {
        UserId userId = newUser();
        Transaction saved = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        Instant newUpdatedAt = Instant.parse("2026-06-22T13:00:00Z");
        LocalDate newTradeDate = LocalDate.parse("2026-06-05");
        Transaction proposed = new Transaction(
                saved.id(), userId, saved.ticker(), saved.operation(),
                saved.quantity(), newTradeDate, saved.createdAt(), newUpdatedAt);

        repository.update(proposed);

        Transaction reloaded = repository.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.tradeDate()).isEqualTo(newTradeDate);
        assertThat(reloaded.operation()).isEqualTo(saved.operation());
        assertThat(reloaded.quantity()).isEqualTo(saved.quantity());
        assertThat(reloaded.ticker()).isEqualTo(saved.ticker());
        assertThat(reloaded.createdAt()).isEqualTo(saved.createdAt());
        assertThat(reloaded.updatedAt()).isEqualTo(newUpdatedAt);
    }

    @Test
    void update_changesOperationOnly_keepsQuantityTradeDateTickerAndCreatedAt() {
        UserId userId = newUser();
        Transaction saved = repository.save(
                newTransaction(userId, AAPL, Operation.BUY, "10", LocalDate.parse("2026-06-01")));
        Instant newUpdatedAt = Instant.parse("2026-06-22T13:00:00Z");
        Transaction proposed = new Transaction(
                saved.id(), userId, saved.ticker(), Operation.SELL,
                saved.quantity(), saved.tradeDate(), saved.createdAt(), newUpdatedAt);

        repository.update(proposed);

        Transaction reloaded = repository.findByIdAndUserId(saved.id(), userId).orElseThrow();
        assertThat(reloaded.operation()).isEqualTo(Operation.SELL);
        assertThat(reloaded.quantity()).isEqualTo(saved.quantity());
        assertThat(reloaded.tradeDate()).isEqualTo(saved.tradeDate());
        assertThat(reloaded.ticker()).isEqualTo(saved.ticker());
        assertThat(reloaded.createdAt()).isEqualTo(saved.createdAt());
        assertThat(reloaded.updatedAt()).isEqualTo(newUpdatedAt);
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static Transaction newTransaction(
            UserId userId, Ticker ticker, Operation operation, String quantity, LocalDate tradeDate) {
        return new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                ticker,
                operation,
                new Quantity(new BigDecimal(quantity)),
                tradeDate,
                TX_CREATED,
                TX_CREATED);
    }
}
