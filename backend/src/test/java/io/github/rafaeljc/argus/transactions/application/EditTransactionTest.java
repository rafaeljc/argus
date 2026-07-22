package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.github.rafaeljc.argus.marketdata.application.EnqueueBackfillJob;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.marketdata.domain.TickerDelistedException;
import io.github.rafaeljc.argus.portfolio.application.HoldingRebuild;
import io.github.rafaeljc.argus.transactions.application.port.TransactionMutationLock;
import io.github.rafaeljc.argus.transactions.application.port.TransactionRepository;
import io.github.rafaeljc.argus.transactions.domain.InsufficientHoldingsException;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.TradeDateFutureException;
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
class EditTransactionTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant ORIGINAL_CREATED_AT = Instant.parse("2026-06-01T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-07-01");
    private static final LocalDate ORIGINAL_TRADE_DATE = LocalDate.parse("2026-06-01");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final TransactionId ID = new TransactionId(UuidCreator.getTimeOrderedEpoch());
    private static final Quantity ORIGINAL_QUANTITY = new Quantity(new BigDecimal("10"));

    @Mock
    private TransactionRepository repository;

    @Mock
    private TransactionMutationLock lock;

    @Mock
    private SymbolLookup symbolLookup;

    @Mock
    private HoldingRebuild holdingRebuild;

    @Mock
    private EnqueueBackfillJob enqueueBackfillJob;

    @Mock
    private ForwardValidator forwardValidator;

    private FixedClock clock;
    private EditTransaction editTransaction;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        editTransaction = new EditTransaction(
                repository, lock, symbolLookup, holdingRebuild, enqueueBackfillJob, forwardValidator, clock);
    }

    @Test
    void edit_quantityOnly_updatesQuantityKeepsOthersAndRebuilds() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalBuy()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(new BigDecimal("7"));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = editTransaction.edit(USER_ID, ID, null, new Quantity(new BigDecimal("7")), null);

        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.ticker()).isEqualTo(TICKER);
        assertThat(result.operation()).isEqualTo(Operation.BUY);
        assertThat(result.quantity()).isEqualTo(new Quantity(new BigDecimal("7")));
        assertThat(result.tradeDate()).isEqualTo(ORIGINAL_TRADE_DATE);
        assertThat(result.createdAt()).isEqualTo(ORIGINAL_CREATED_AT);
        assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);
        verify(repository).update(any());
        verify(holdingRebuild).apply(USER_ID, TICKER, new BigDecimal("7"));
    }

    @Test
    void edit_tradeDateOnlyMovedLater_updatesTradeDateAndSkipsBackfill() {
        LocalDate laterDate = ORIGINAL_TRADE_DATE.plusDays(9);
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalBuy()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(ORIGINAL_QUANTITY.value());
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = editTransaction.edit(USER_ID, ID, null, null, laterDate);

        assertThat(result.tradeDate()).isEqualTo(laterDate);
        assertThat(result.quantity()).isEqualTo(ORIGINAL_QUANTITY);
        verifyNoInteractions(enqueueBackfillJob);
    }

    @Test
    void edit_becomesBuyOnDelistedTicker_throwsTickerDelistedAndDoesNotUpdate() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalSell()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(true);

        assertThatThrownBy(() -> editTransaction.edit(USER_ID, ID, Operation.BUY, null, null))
                .isInstanceOf(TickerDelistedException.class)
                .extracting("ticker")
                .isEqualTo(TICKER);

        verify(repository, never()).update(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob, forwardValidator);
    }

    @Test
    void edit_becomesBuyNotDelisted_updates() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalSell()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(new BigDecimal("20"));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = editTransaction.edit(USER_ID, ID, Operation.BUY, null, null);

        assertThat(result.operation()).isEqualTo(Operation.BUY);
        verify(repository).update(any());
    }

    @Test
    void edit_remainsSell_skipsDelistedCheck() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalSell()));
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(BigDecimal.ZERO);

        editTransaction.edit(USER_ID, ID, null, new Quantity(new BigDecimal("3")), null);

        verify(symbolLookup, never()).isDelisted(any());
    }

    @Test
    void edit_selfOversell_throwsInsufficientHoldingsAfterUpdating() {
        Transaction current = originalSell();
        Quantity increasedQuantity = new Quantity(new BigDecimal("50"));
        Transaction oversoldSelf = new Transaction(
                ID, USER_ID, TICKER, Operation.SELL, increasedQuantity, ORIGINAL_TRADE_DATE,
                ORIGINAL_CREATED_AT, FIXED_NOW);
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(current));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE))
                .thenReturn(Optional.of(new ForwardValidator.Oversold(oversoldSelf, new BigDecimal("12"))));

        assertThatThrownBy(() -> editTransaction.edit(USER_ID, ID, null, increasedQuantity, null))
                .isInstanceOf(InsufficientHoldingsException.class)
                .extracting("ticker", "held", "attempted")
                .containsExactly(TICKER, new BigDecimal("12"), increasedQuantity);

        verify(repository).update(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void edit_wouldInvalidateLaterSell_throwsTransactionMutationRejectedAfterUpdating() {
        Transaction laterSell = new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()), USER_ID, TICKER, Operation.SELL,
                new Quantity(new BigDecimal("15")), ORIGINAL_TRADE_DATE.plusDays(5), FIXED_NOW, FIXED_NOW);
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalBuy()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE))
                .thenReturn(Optional.of(new ForwardValidator.Oversold(laterSell, new BigDecimal("2"))));

        assertThatThrownBy(() -> editTransaction.edit(
                        USER_ID, ID, null, new Quantity(new BigDecimal("3")), null))
                .isInstanceOfSatisfying(TransactionMutationRejectedException.class, ex -> {
                    assertThat(ex.details()).hasSize(1);
                    assertThat(ex.details().get(0).field()).isEqualTo("trade_date");
                    assertThat(ex.details().get(0).code()).isEqualTo("would_invalidate_sell");
                });

        verify(repository).update(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void edit_unknownTransaction_throwsResourceNotFoundAndDoesNotUpdate() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> editTransaction.edit(USER_ID, ID, null, ORIGINAL_QUANTITY, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).update(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob, forwardValidator);
    }

    @Test
    void edit_futureTradeDate_throwsTradeDateFutureAndDoesNotUpdate() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalBuy()));
        LocalDate futureDate = TODAY.plusDays(1);

        assertThatThrownBy(() -> editTransaction.edit(USER_ID, ID, null, null, futureDate))
                .isInstanceOf(TradeDateFutureException.class)
                .extracting("tradeDate")
                .isEqualTo(futureDate);

        verify(repository, never()).update(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob, forwardValidator);
    }

    @Test
    void edit_tradeDateMovedEarlier_enqueuesBackfillFromNewDate() {
        LocalDate earlierDate = ORIGINAL_TRADE_DATE.minusDays(10);
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalBuy()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, earlierDate)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(ORIGINAL_QUANTITY.value());
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = editTransaction.edit(USER_ID, ID, null, null, earlierDate);

        assertThat(result.tradeDate()).isEqualTo(earlierDate);
        verify(enqueueBackfillJob).apply(USER_ID, TICKER, earlierDate.minusYears(5), TODAY);
    }

    @Test
    void edit_locksBeforeLoadingTransaction() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> editTransaction.edit(USER_ID, ID, null, ORIGINAL_QUANTITY, null))
                .isInstanceOf(ResourceNotFoundException.class);

        InOrder order = Mockito.inOrder(lock, repository);
        order.verify(lock).acquireForUser(USER_ID);
        order.verify(repository).findByIdAndUserId(ID, USER_ID);
    }

    @Test
    void edit_updatesBeforeConsultingForwardValidator() {
        when(repository.findByIdAndUserId(ID, USER_ID)).thenReturn(Optional.of(originalBuy()));
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(forwardValidator.firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE)).thenReturn(Optional.empty());
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(ORIGINAL_QUANTITY.value());

        editTransaction.edit(USER_ID, ID, null, ORIGINAL_QUANTITY, null);

        InOrder order = Mockito.inOrder(repository, forwardValidator);
        order.verify(repository).update(any());
        order.verify(forwardValidator).firstOversoldSell(USER_ID, TICKER, ORIGINAL_TRADE_DATE);
    }

    private static Transaction originalBuy() {
        return new Transaction(
                ID, USER_ID, TICKER, Operation.BUY, ORIGINAL_QUANTITY, ORIGINAL_TRADE_DATE,
                ORIGINAL_CREATED_AT, ORIGINAL_CREATED_AT);
    }

    private static Transaction originalSell() {
        return new Transaction(
                ID, USER_ID, TICKER, Operation.SELL, ORIGINAL_QUANTITY, ORIGINAL_TRADE_DATE,
                ORIGINAL_CREATED_AT, ORIGINAL_CREATED_AT);
    }
}
