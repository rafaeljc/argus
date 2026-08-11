package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.FieldError;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.EnqueueSnapshotRebuild;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeleteTransaction {

    private final TransactionRepository repository;
    private final TransactionalMutationLock lock;
    private final HoldingRebuild holdingRebuild;
    private final EnqueueSnapshotRebuild enqueueSnapshotRebuild;
    private final ForwardValidator forwardValidator;
    private final Clock clock;

    public DeleteTransaction(
            TransactionRepository repository,
            TransactionalMutationLock lock,
            HoldingRebuild holdingRebuild,
            EnqueueSnapshotRebuild enqueueSnapshotRebuild,
            ForwardValidator forwardValidator,
            Clock clock) {
        this.repository = repository;
        this.lock = lock;
        this.holdingRebuild = holdingRebuild;
        this.enqueueSnapshotRebuild = enqueueSnapshotRebuild;
        this.forwardValidator = forwardValidator;
        this.clock = clock;
    }

    public void delete(UserId userId, TransactionId id) {
        lock.acquireResourceById("transaction", userId.value());

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
        enqueueSnapshotRebuild.apply(userId);
    }
}
