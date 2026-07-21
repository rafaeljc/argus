package io.github.rafaeljc.argus.transactions.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String ticker,
        String operation,
        String quantity,
        @JsonProperty("trade_date") LocalDate tradeDate,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id().value(),
                transaction.ticker().value(),
                transaction.operation().name(),
                transaction.quantity().value().toPlainString(),
                transaction.tradeDate(),
                transaction.createdAt(),
                transaction.updatedAt());
    }
}
