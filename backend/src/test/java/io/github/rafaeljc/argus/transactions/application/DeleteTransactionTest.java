package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteTransactionTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-07-01");
    private static final LocalDate TRADE_DATE = LocalDate.parse("2026-06-01");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final TransactionId ID = new TransactionId(UuidCreator.getTimeOrderedEpoch());
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("10"));

    @Mock
    private TransactionRepository repository;

    @Mock
    private TransactionMutationLock lock;

    @Mock
    private HoldingRebuild holdingRebuild;

    @Mock
    private ForwardValidator forwardValidator;

    private FixedClock clock;
    private DeleteTransaction deleteTransaction;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        deleteTransaction = new DeleteTransaction(repository, lock, holdingRebuild, forwardValidator, clock);
    }

    @Test
    void delete_noLaterSellDependency_deletesAndRebuilds() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(buyTransaction()));
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(BigDecimal.ZERO);

        deleteTransaction.delete(USER_ID, ID);

        verify(repository).deleteByIdAndUserId(ID, USER_ID);
        verify(holdingRebuild).apply(USER_ID, TICKER, BigDecimal.ZERO);
    }

    @Test
    void delete_buyWithLaterSellDependency_throwsTransactionMutationRejectedAndDoesNotRebuild() {
        Transaction laterSell = new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()), USER_ID, TICKER, Operation.SELL,
                new Quantity(new BigDecimal("15")), TRADE_DATE.plusDays(5), FIXED_NOW, FIXED_NOW);
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(buyTransaction()));
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, TRADE_DATE))
                .thenReturn(Optional.of(new ForwardValidator.Oversold(laterSell, new BigDecimal("5"))));

        assertThatThrownBy(() -> deleteTransaction.delete(USER_ID, ID))
                .isInstanceOfSatisfying(TransactionMutationRejectedException.class, ex -> {
                    assertThat(ex.details()).hasSize(1);
                    assertThat(ex.details().get(0).field()).isEqualTo("trade_date");
                    assertThat(ex.details().get(0).code()).isEqualTo("would_invalidate_sell");
                });

        verify(repository).deleteByIdAndUserId(ID, USER_ID);
        verifyNoInteractions(holdingRebuild);
    }

    @Test
    void delete_unknownTransaction_throwsResourceNotFoundAndDoesNotDelete() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteTransaction.delete(USER_ID, ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).deleteByIdAndUserId(ID, USER_ID);
        verifyNoInteractions(holdingRebuild, forwardValidator);
    }

    @Test
    void delete_locksBeforeLoadingTransaction() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteTransaction.delete(USER_ID, ID))
                .isInstanceOf(ResourceNotFoundException.class);

        InOrder order = Mockito.inOrder(lock, repository);
        order.verify(lock).acquireForUser(USER_ID);
        order.verify(repository).findByIdAndUserId(ID, USER_ID);
    }

    @Test
    void delete_deletesBeforeConsultingForwardValidator() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(buyTransaction()));
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(BigDecimal.ZERO);

        deleteTransaction.delete(USER_ID, ID);

        InOrder order = Mockito.inOrder(repository, forwardValidator);
        order.verify(repository).deleteByIdAndUserId(ID, USER_ID);
        order.verify(forwardValidator).firstOversoldSell(USER_ID, TICKER, TRADE_DATE);
    }

    private static Transaction buyTransaction() {
        return new Transaction(ID, USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE, FIXED_NOW, FIXED_NOW);
    }
}
