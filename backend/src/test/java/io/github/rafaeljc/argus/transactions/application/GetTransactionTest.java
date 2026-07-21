package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTransactionTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final TransactionId TRANSACTION_ID = new TransactionId(UuidCreator.getTimeOrderedEpoch());
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    @Mock
    private TransactionRepository repository;

    private GetTransaction getTransaction;

    @BeforeEach
    void setUp() {
        getTransaction = new GetTransaction(repository);
    }

    @Test
    void get_found_returnsTransaction() {
        Transaction transaction = new Transaction(
                TRANSACTION_ID, USER_ID, new Ticker("AAPL"), Operation.BUY,
                new Quantity(new BigDecimal("10")), LocalDate.parse("2026-06-15"), NOW, NOW);
        when(repository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.of(transaction));

        Transaction result = getTransaction.get(USER_ID, TRANSACTION_ID);

        assertThat(result).isEqualTo(transaction);
    }

    @Test
    void get_missing_throwsResourceNotFound() {
        when(repository.findByIdAndUserId(TRANSACTION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getTransaction.get(USER_ID, TRANSACTION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
