package io.github.rafaeljc.argus.transactions.domain;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;

public record Transaction(
        TransactionId id,
        UserId userId,
        Ticker ticker,
        Operation operation,
        Quantity quantity,
        LocalDate tradeDate,
        Instant createdAt,
        Instant updatedAt) {

    public Transaction {
        if (id == null) {
            throw new IllegalArgumentException("Transaction id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Transaction userId must not be null");
        }
        if (ticker == null) {
            throw new IllegalArgumentException("Transaction ticker must not be null");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Transaction operation must not be null");
        }
        if (quantity == null) {
            throw new IllegalArgumentException("Transaction quantity must not be null");
        }
        if (tradeDate == null) {
            throw new IllegalArgumentException("Transaction tradeDate must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Transaction createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Transaction updatedAt must not be null");
        }
    }
}
