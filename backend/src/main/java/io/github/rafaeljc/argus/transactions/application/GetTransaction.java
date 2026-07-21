package io.github.rafaeljc.argus.transactions.application;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import org.springframework.stereotype.Service;

@Service
public class GetTransaction {

    private final TransactionRepository repository;

    public GetTransaction(TransactionRepository repository) {
        this.repository = repository;
    }

    public Transaction get(UserId userId, TransactionId id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("transaction not found: " + id.value()));
    }
}
