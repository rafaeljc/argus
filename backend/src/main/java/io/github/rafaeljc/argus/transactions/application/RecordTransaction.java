package io.github.rafaeljc.argus.transactions.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.FieldError;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.EnqueueBackfillJob;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.marketdata.domain.TickerDelistedException;
import io.github.rafaeljc.argus.marketdata.domain.TickerNotFoundException;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.InsufficientHoldingsException;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.TradeDateFutureException;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecordTransaction {

    private static final int BACKFILL_LOOKBACK_YEARS = 5;

    private final TransactionRepository repository;
    private final TransactionMutationLock lock;
    private final SymbolLookup symbolLookup;
    private final HoldingRebuild holdingRebuild;
    private final EnqueueBackfillJob enqueueBackfillJob;
    private final Clock clock;

    public RecordTransaction(
            TransactionRepository repository,
            TransactionMutationLock lock,
            SymbolLookup symbolLookup,
            HoldingRebuild holdingRebuild,
            EnqueueBackfillJob enqueueBackfillJob,
            Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.symbolLookup = symbolLookup;
        this.holdingRebuild = holdingRebuild;
        this.enqueueBackfillJob = enqueueBackfillJob;
        this.clock = clock;
    }

    public Transaction record(
            UserId userId, Ticker ticker, Operation operation, Quantity quantity, LocalDate tradeDate) {
        lock.acquireForUser(userId);

        if (!symbolLookup.exists(ticker)) {
            throw new TickerNotFoundException(ticker);
        }
        if (operation == Operation.BUY && symbolLookup.isDelisted(ticker)) {
            throw new TickerDelistedException(ticker);
        }
        LocalDate today = clock.today();
        if (tradeDate.isAfter(today)) {
            throw new TradeDateFutureException(tradeDate);
        }
        if (operation == Operation.SELL) {
            BigDecimal held = repository.holdingsAsOf(userId, ticker, tradeDate);
            if (held.compareTo(quantity.value()) < 0) {
                throw new InsufficientHoldingsException(ticker, held, quantity);
            }
            rejectIfInvalidatesLaterSells(userId, ticker, quantity, tradeDate, held);
        }

        Transaction saved = repository.save(new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()),
                userId, ticker, operation, quantity, tradeDate, clock.now(), clock.now()));

        BigDecimal ledgerNetQuantity = repository.holdingsAsOf(userId, ticker, today);
        holdingRebuild.apply(userId, ticker, ledgerNetQuantity);

        enqueueBackfillJob.apply(userId, ticker, tradeDate.minusYears(BACKFILL_LOOKBACK_YEARS), today);

        return saved;
    }

    private void rejectIfInvalidatesLaterSells(
            UserId userId, Ticker ticker, Quantity newQuantity, LocalDate tradeDate, BigDecimal heldAsOfTradeDate) {
        List<Transaction> laterTransactions = repository.findAllAfter(userId, ticker, tradeDate);
        BigDecimal running = heldAsOfTradeDate.subtract(newQuantity.value());
        for (Transaction laterTx : laterTransactions) {
            if (laterTx.operation() == Operation.BUY) {
                running = running.add(laterTx.quantity().value());
                continue;
            }
            running = running.subtract(laterTx.quantity().value());
            if (running.signum() < 0) {
                throw new TransactionMutationRejectedException(List.of(new FieldError(
                        "trade_date",
                        "would_invalidate_sell",
                        "sell " + laterTx.id().value() + " on " + laterTx.tradeDate() + " would be oversold")));
            }
        }
    }
}
