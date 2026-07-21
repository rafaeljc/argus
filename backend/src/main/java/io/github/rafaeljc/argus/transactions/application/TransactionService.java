package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final RecordTransaction recordTransaction;
    private final ListTransactions listTransactions;
    private final GetTransaction getTransaction;

    public TransactionService(
            RecordTransaction recordTransaction, ListTransactions listTransactions, GetTransaction getTransaction) {
        this.recordTransaction = recordTransaction;
        this.listTransactions = listTransactions;
        this.getTransaction = getTransaction;
    }

    @Transactional
    public Transaction record(
            UserId userId, Ticker ticker, Operation operation, Quantity quantity, LocalDate tradeDate) {
        return recordTransaction.record(userId, ticker, operation, quantity, tradeDate);
    }

    @Transactional(readOnly = true)
    public PageResult<Transaction> list(UserId userId, int page, int perPage) {
        return listTransactions.list(userId, page, perPage);
    }

    @Transactional(readOnly = true)
    public Transaction get(UserId userId, TransactionId id) {
        return getTransaction.get(userId, id);
    }
}
