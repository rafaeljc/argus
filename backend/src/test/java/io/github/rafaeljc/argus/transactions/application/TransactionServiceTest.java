package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("10"));
    private static final LocalDate TRADE_DATE = LocalDate.parse("2026-06-15");
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final TransactionId TRANSACTION_ID = new TransactionId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private RecordTransaction recordTransaction;

    @Mock
    private ListTransactions listTransactions;

    @Mock
    private GetTransaction getTransaction;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(recordTransaction, listTransactions, getTransaction);
    }

    @Test
    void record_delegatesToRecordTransactionAndReturnsItsResult() {
        Transaction expected = new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()), USER_ID, TICKER,
                Operation.BUY, QUANTITY, TRADE_DATE, NOW, NOW);
        when(recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE))
                .thenReturn(expected);

        Transaction result = service.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE);

        assertThat(result).isEqualTo(expected);
        verify(recordTransaction).record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE);
    }

    @Test
    void list_delegatesToListTransactionsAndReturnsItsResult() {
        Transaction transaction = new Transaction(
                TRANSACTION_ID, USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE, NOW, NOW);
        PageResult<Transaction> expected = new PageResult<>(List.of(transaction), 1, 1, 50);
        when(listTransactions.list(USER_ID, 1, 50)).thenReturn(expected);

        PageResult<Transaction> result = service.list(USER_ID, 1, 50);

        assertThat(result).isEqualTo(expected);
        verify(listTransactions).list(USER_ID, 1, 50);
    }

    @Test
    void get_delegatesToGetTransactionAndReturnsItsResult() {
        Transaction expected = new Transaction(
                TRANSACTION_ID, USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE, NOW, NOW);
        when(getTransaction.get(USER_ID, TRANSACTION_ID)).thenReturn(expected);

        Transaction result = service.get(USER_ID, TRANSACTION_ID);

        assertThat(result).isEqualTo(expected);
        verify(getTransaction).get(USER_ID, TRANSACTION_ID);
    }
}
