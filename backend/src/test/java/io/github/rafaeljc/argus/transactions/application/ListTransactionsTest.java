package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
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
class ListTransactionsTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private TransactionRepository repository;

    private ListTransactions listTransactions;

    @BeforeEach
    void setUp() {
        listTransactions = new ListTransactions(repository);
    }

    @Test
    void list_delegatesPageAndPerPageToRepositoryAndReturnsItemsWithTotal() {
        Transaction transaction = new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()), USER_ID, new Ticker("AAPL"),
                Operation.BUY, new Quantity(new BigDecimal("10")), LocalDate.parse("2026-06-15"), NOW, NOW);
        when(repository.listByUserId(USER_ID, 2, 25)).thenReturn(List.of(transaction));
        when(repository.countByUserId(USER_ID)).thenReturn(30);

        PageResult<Transaction> result = listTransactions.list(USER_ID, 2, 25);

        assertThat(result.items()).containsExactly(transaction);
        assertThat(result.total()).isEqualTo(30);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.perPage()).isEqualTo(25);
    }

    @Test
    void list_noTransactions_returnsEmptyPageWithZeroTotal() {
        when(repository.listByUserId(USER_ID, 1, 50)).thenReturn(List.of());
        when(repository.countByUserId(USER_ID)).thenReturn(0);

        PageResult<Transaction> result = listTransactions.list(USER_ID, 1, 50);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
