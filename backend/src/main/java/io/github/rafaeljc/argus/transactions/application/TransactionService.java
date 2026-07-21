package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final RecordTransaction recordTransaction;

    public TransactionService(RecordTransaction recordTransaction) {
        this.recordTransaction = recordTransaction;
    }

    @Transactional
    public Transaction record(
            UserId userId, Ticker ticker, Operation operation, Quantity quantity, LocalDate tradeDate) {
        return recordTransaction.record(userId, ticker, operation, quantity, tradeDate);
    }
}
