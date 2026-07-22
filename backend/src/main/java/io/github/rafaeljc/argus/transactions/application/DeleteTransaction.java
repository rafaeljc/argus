package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.FieldError;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeleteTransaction {

    private final TransactionRepository repository;
    private final TransactionMutationLock lock;
    private final HoldingRebuild holdingRebuild;
    private final ForwardValidator forwardValidator;
    private final Clock clock;

    public DeleteTransaction(
            TransactionRepository repository,
            TransactionMutationLock lock,
            HoldingRebuild holdingRebuild,
            ForwardValidator forwardValidator,
            Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.holdingRebuild = holdingRebuild;
        this.forwardValidator = forwardValidator;
        this.clock = clock;
    }

    public void delete(UserId userId, TransactionId id) {
        lock.acquireForUser(userId);

        Transaction current = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("transaction not found: " + id.value()));

        repository.deleteByIdAndUserId(id, userId);

        forwardValidator.firstOversoldSell(userId, current.ticker(), current.tradeDate()).ifPresent(oversold -> {
            throw new TransactionMutationRejectedException(List.of(new FieldError(
                    "trade_date",
                    "would_invalidate_sell",
                    "sell " + oversold.sell().id().value() + " on " + oversold.sell().tradeDate()
                            + " would be oversold")));
        });

        holdingRebuild.apply(
                userId, current.ticker(), repository.holdingsAsOf(userId, current.ticker(), clock.today()));
    }
}
