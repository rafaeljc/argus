package io.github.rafaeljc.argus.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final TransactionId ID = new TransactionId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final Operation OPERATION = Operation.BUY;
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("10"));
    private static final LocalDate TRADE_DATE = LocalDate.parse("2026-06-01");
    private static final Instant CREATED_AT = Instant.parse("2026-06-22T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    private static Transaction newTransaction() {
        return new Transaction(ID, USER_ID, TICKER, OPERATION, QUANTITY, TRADE_DATE, CREATED_AT, UPDATED_AT);
    }

    @Test
    void constructor_validInput_setsAllFields() {
        Transaction tx = newTransaction();

        assertThat(tx.id()).isEqualTo(ID);
        assertThat(tx.userId()).isEqualTo(USER_ID);
        assertThat(tx.ticker()).isEqualTo(TICKER);
        assertThat(tx.operation()).isEqualTo(OPERATION);
        assertThat(tx.quantity()).isEqualTo(QUANTITY);
        assertThat(tx.tradeDate()).isEqualTo(TRADE_DATE);
        assertThat(tx.createdAt()).isEqualTo(CREATED_AT);
        assertThat(tx.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(null, USER_ID, TICKER, OPERATION, QUANTITY, TRADE_DATE, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, null, TICKER, OPERATION, QUANTITY, TRADE_DATE, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void constructor_nullTicker_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, USER_ID, null, OPERATION, QUANTITY, TRADE_DATE, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void constructor_nullOperation_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, USER_ID, TICKER, null, QUANTITY, TRADE_DATE, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation");
    }

    @Test
    void constructor_nullQuantity_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, USER_ID, TICKER, OPERATION, null, TRADE_DATE, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void constructor_nullTradeDate_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, USER_ID, TICKER, OPERATION, QUANTITY, null, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tradeDate");
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, USER_ID, TICKER, OPERATION, QUANTITY, TRADE_DATE, null, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void constructor_nullUpdatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new Transaction(ID, USER_ID, TICKER, OPERATION, QUANTITY, TRADE_DATE, CREATED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }
}
