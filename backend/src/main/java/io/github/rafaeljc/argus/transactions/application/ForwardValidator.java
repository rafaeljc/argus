package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ForwardValidator {

    private final TransactionRepository repository;

    ForwardValidator(TransactionRepository repository) {
        this.repository = repository;
    }

    record Oversold(Transaction sell, BigDecimal heldBefore) {}

    Optional<Oversold> firstOversoldSell(UserId userId, Ticker ticker, LocalDate startDate) {
        // -1 day: keeps startDate's own transactions inside the replay loop below instead of
        // pre-netting them into the starting balance, so same-day ordering (by createdAt) is
        // still checked transaction-by-transaction rather than collapsed into one net figure.
        LocalDate cutoff = startDate.minusDays(1);
        BigDecimal running = repository.holdingsAsOf(userId, ticker, cutoff);
        List<Transaction> fromStart =
                repository.findAllAfterOrderedByTradeDateThenCreatedAt(userId, ticker, cutoff);

        for (Transaction tx : fromStart) {
            if (tx.operation() == Operation.BUY) {
                running = running.add(tx.quantity().value());
                continue;
            }
            if (running.compareTo(tx.quantity().value()) < 0) {
                return Optional.of(new Oversold(tx, running));
            }
            running = running.subtract(tx.quantity().value());
        }
        return Optional.empty();
    }
}
