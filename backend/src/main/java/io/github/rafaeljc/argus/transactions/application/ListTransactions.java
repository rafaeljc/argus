package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import org.springframework.stereotype.Service;

@Service
public class ListTransactions {

    private final TransactionRepository repository;

    public ListTransactions(TransactionRepository repository) {
        this.repository = repository;
    }

    public PageResult<Transaction> list(UserId userId, int page, int perPage) {
        return new PageResult<>(
                repository.listByUserId(userId, page, perPage),
                repository.countByUserId(userId),
                page,
                perPage);
    }
}
