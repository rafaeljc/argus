package io.github.rafaeljc.argus.transactions.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionResponseTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant CREATED_AT = Instant.parse("2026-06-01T12:34:56Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-15T08:00:00Z");

    @Test
    void from_projectsContractFields() {
        Transaction transaction = new Transaction(
                new TransactionId(ID),
                new UserId(UuidCreator.getTimeOrderedEpoch()),
                new Ticker("AAPL"),
                Operation.SELL,
                new Quantity(new BigDecimal("10.5")),
                LocalDate.parse("2026-06-01"),
                CREATED_AT, UPDATED_AT);

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.id()).isEqualTo(ID);
        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.operation()).isEqualTo("SELL");
        assertThat(response.quantity()).isEqualTo("10.500000");
        assertThat(response.tradeDate()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }
}
