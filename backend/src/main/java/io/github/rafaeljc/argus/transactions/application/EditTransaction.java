package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.FieldError;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.EnqueueBackfillJob;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.marketdata.domain.TickerDelistedException;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.InsufficientHoldingsException;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.TradeDateFutureException;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EditTransaction {

    private static final int BACKFILL_LOOKBACK_YEARS = 5;

    private final TransactionRepository repository;
    private final TransactionMutationLock lock;
    private final SymbolLookup symbolLookup;
    private final HoldingRebuild holdingRebuild;
    private final EnqueueBackfillJob enqueueBackfillJob;
    private final ForwardValidator forwardValidator;
    private final Clock clock;

    public EditTransaction(
            TransactionRepository repository,
            TransactionMutationLock lock,
            SymbolLookup symbolLookup,
            HoldingRebuild holdingRebuild,
            EnqueueBackfillJob enqueueBackfillJob,
            ForwardValidator forwardValidator,
            Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.symbolLookup = symbolLookup;
        this.holdingRebuild = holdingRebuild;
        this.enqueueBackfillJob = enqueueBackfillJob;
        this.forwardValidator = forwardValidator;
        this.clock = clock;
    }

    public Transaction edit(
            UserId userId, TransactionId id, Operation operation, Quantity quantity, LocalDate tradeDate) {
        lock.acquireForUser(userId);

        Transaction current = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("transaction not found: " + id.value()));

        Operation proposedOperation = operation != null ? operation : current.operation();
        Quantity proposedQuantity = quantity != null ? quantity : current.quantity();
        LocalDate proposedTradeDate = tradeDate != null ? tradeDate : current.tradeDate();
        Ticker ticker = current.ticker();

        if (proposedOperation == Operation.BUY && symbolLookup.isDelisted(ticker)) {
            throw new TickerDelistedException(ticker);
        }
        LocalDate today = clock.today();
        if (proposedTradeDate.isAfter(today)) {
            throw new TradeDateFutureException(proposedTradeDate);
        }

        Transaction proposed = repository.update(new Transaction(
                current.id(), userId, ticker, proposedOperation, proposedQuantity, proposedTradeDate,
                current.createdAt(), clock.now()));

        LocalDate startDate = proposedTradeDate.isBefore(current.tradeDate())
                ? proposedTradeDate
                : current.tradeDate();
        rejectIfOversold(userId, ticker, startDate, proposed);

        holdingRebuild.apply(userId, ticker, repository.holdingsAsOf(userId, ticker, today));

        if (proposedTradeDate.isBefore(current.tradeDate())) {
            enqueueBackfillJob.apply(userId, ticker, proposedTradeDate.minusYears(BACKFILL_LOOKBACK_YEARS), today);
        }

        return proposed;
    }

    private void rejectIfOversold(UserId userId, Ticker ticker, LocalDate startDate, Transaction proposed) {
        forwardValidator.firstOversoldSell(userId, ticker, startDate).ifPresent(oversold -> {
            if (oversold.sell().id().equals(proposed.id())) {
                throw new InsufficientHoldingsException(ticker, oversold.heldBefore(), proposed.quantity());
            }
            throw new TransactionMutationRejectedException(List.of(new FieldError(
                    "trade_date",
                    "would_invalidate_sell",
                    "sell " + oversold.sell().id().value() + " on " + oversold.sell().tradeDate()
                            + " would be oversold")));
        });
    }
}
