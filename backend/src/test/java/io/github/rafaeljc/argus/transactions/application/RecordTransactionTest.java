package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.TransactionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.EnqueueBackfillJob;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.marketdata.domain.TickerDelistedException;
import io.github.rafaeljc.argus.marketdata.domain.TickerNotFoundException;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordTransactionTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-07-01");
    private static final LocalDate TRADE_DATE = LocalDate.parse("2026-06-15");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("10"));

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

    private FixedClock clock;
    private RecordTransaction recordTransaction;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        recordTransaction = new RecordTransaction(
                repository, lock, symbolLookup, holdingRebuild, enqueueBackfillJob, clock);
    }

    @Test
    void record_validBuy_savesAndRebuildsAndEnqueuesInOrder() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(new BigDecimal("10"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction saved = recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE);

        assertThat(saved.userId()).isEqualTo(USER_ID);
        assertThat(saved.ticker()).isEqualTo(TICKER);
        assertThat(saved.operation()).isEqualTo(Operation.BUY);
        assertThat(saved.quantity()).isEqualTo(QUANTITY);
        assertThat(saved.tradeDate()).isEqualTo(TRADE_DATE);
        assertThat(saved.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.updatedAt()).isEqualTo(FIXED_NOW);

        InOrder order = Mockito.inOrder(lock, symbolLookup, repository, holdingRebuild, enqueueBackfillJob);
        order.verify(lock).acquireForUser(USER_ID);
        order.verify(symbolLookup).exists(TICKER);
        order.verify(symbolLookup).isDelisted(TICKER);
        order.verify(repository).save(any());
        order.verify(repository).holdingsAsOf(USER_ID, TICKER, TODAY);
        order.verify(holdingRebuild).apply(USER_ID, TICKER, new BigDecimal("10"));
        order.verify(enqueueBackfillJob).apply(
                eq(USER_ID), eq(TICKER), eq(TRADE_DATE.minusYears(5)), eq(TODAY));
    }

    @Test
    void record_validSellWithPriorBuy_savesAndRebuilds() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(repository.holdingsAsOf(USER_ID, TICKER, TRADE_DATE)).thenReturn(new BigDecimal("20"));
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(new BigDecimal("10"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction saved = recordTransaction.record(USER_ID, TICKER, Operation.SELL, QUANTITY, TRADE_DATE);

        assertThat(saved.operation()).isEqualTo(Operation.SELL);
        verify(repository).save(any());
        verify(holdingRebuild).apply(USER_ID, TICKER, new BigDecimal("10"));
    }

    @Test
    void record_sellSkipsDelistedCheck() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(repository.holdingsAsOf(USER_ID, TICKER, TRADE_DATE)).thenReturn(new BigDecimal("20"));
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(new BigDecimal("10"));

        recordTransaction.record(USER_ID, TICKER, Operation.SELL, QUANTITY, TRADE_DATE);

        verify(symbolLookup, never()).isDelisted(any());
    }

    @Test
    void record_sellOversellAsOfTradeDate_throwsInsufficientHoldingsAndDoesNotSave() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(repository.holdingsAsOf(USER_ID, TICKER, TRADE_DATE)).thenReturn(new BigDecimal("5"));

        assertThatThrownBy(() -> recordTransaction.record(USER_ID, TICKER, Operation.SELL, QUANTITY, TRADE_DATE))
                .isInstanceOf(InsufficientHoldingsException.class)
                .extracting("ticker", "held", "attempted")
                .containsExactly(TICKER, new BigDecimal("5"), QUANTITY);

        verify(repository, never()).save(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void record_unknownTicker_throwsTickerNotFoundAndDoesNotSave() {
        when(symbolLookup.exists(TICKER)).thenReturn(false);

        assertThatThrownBy(() -> recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE))
                .isInstanceOf(TickerNotFoundException.class)
                .extracting("ticker")
                .isEqualTo(TICKER);

        verify(repository, never()).save(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void record_delistedBuy_throwsTickerDelistedAndDoesNotSave() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(symbolLookup.isDelisted(TICKER)).thenReturn(true);

        assertThatThrownBy(() -> recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE))
                .isInstanceOf(TickerDelistedException.class)
                .extracting("ticker")
                .isEqualTo(TICKER);

        verify(repository, never()).save(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void record_futureTradeDate_throwsTradeDateFutureAndDoesNotSave() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        LocalDate futureDate = TODAY.plusDays(1);

        assertThatThrownBy(() -> recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, futureDate))
                .isInstanceOf(TradeDateFutureException.class)
                .extracting("tradeDate")
                .isEqualTo(futureDate);

        verify(repository, never()).save(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void record_unknownTicker_lockIsAcquiredBeforeValidation() {
        when(symbolLookup.exists(TICKER)).thenReturn(false);

        assertThatThrownBy(() -> recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE))
                .isInstanceOf(TickerNotFoundException.class);

        InOrder order = Mockito.inOrder(lock, symbolLookup);
        order.verify(lock).acquireForUser(USER_ID);
        order.verify(symbolLookup).exists(TICKER);
    }

    @Test
    void record_backdatedSellWouldInvalidateLaterSell_throwsTransactionMutationRejectedAndDoesNotSave() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(repository.holdingsAsOf(USER_ID, TICKER, TRADE_DATE)).thenReturn(new BigDecimal("20"));
        LocalDate laterDate = TRADE_DATE.plusDays(5);
        Transaction laterSell = laterTransaction(Operation.SELL, "15", laterDate);
        when(repository.findAllAfter(USER_ID, TICKER, TRADE_DATE)).thenReturn(List.of(laterSell));

        assertThatThrownBy(() -> recordTransaction.record(USER_ID, TICKER, Operation.SELL, QUANTITY, TRADE_DATE))
                .isInstanceOfSatisfying(TransactionMutationRejectedException.class, ex -> {
                    assertThat(ex.details()).hasSize(1);
                    assertThat(ex.details().get(0).field()).isEqualTo("trade_date");
                    assertThat(ex.details().get(0).code()).isEqualTo("would_invalidate_sell");
                });

        verify(repository, never()).save(any());
        verifyNoInteractions(holdingRebuild, enqueueBackfillJob);
    }

    @Test
    void record_backdatedSellLeavesLaterSellStillValid_saves() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(repository.holdingsAsOf(USER_ID, TICKER, TRADE_DATE)).thenReturn(new BigDecimal("20"));
        LocalDate laterDate = TRADE_DATE.plusDays(5);
        Transaction laterSell = laterTransaction(Operation.SELL, "10", laterDate);
        when(repository.findAllAfter(USER_ID, TICKER, TRADE_DATE)).thenReturn(List.of(laterSell));
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(BigDecimal.ZERO);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction saved = recordTransaction.record(USER_ID, TICKER, Operation.SELL, QUANTITY, TRADE_DATE);

        assertThat(saved.operation()).isEqualTo(Operation.SELL);
        verify(repository).save(any());
    }

    @Test
    void record_backdatedSellWithInterveningLaterBuy_accountsForItAndSaves() {
        // held(tradeDate)=5, new sell=5 -> running starts at 0. A naive implementation that
        // only replays later SELLs (ignoring later BUYs) would see running go to 0-8=-8 at the
        // later sell and wrongly reject; correctly replaying the intervening +10 BUY first
        // leaves running at 10-8=2, which must not throw.
        Quantity sellQuantity = new Quantity(new BigDecimal("5"));
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(repository.holdingsAsOf(USER_ID, TICKER, TRADE_DATE)).thenReturn(new BigDecimal("5"));
        LocalDate laterBuyDate = TRADE_DATE.plusDays(2);
        LocalDate laterSellDate = TRADE_DATE.plusDays(5);
        Transaction laterBuy = laterTransaction(Operation.BUY, "10", laterBuyDate);
        Transaction laterSell = laterTransaction(Operation.SELL, "8", laterSellDate);
        when(repository.findAllAfter(USER_ID, TICKER, TRADE_DATE)).thenReturn(List.of(laterBuy, laterSell));
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(BigDecimal.ZERO);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction saved = recordTransaction.record(USER_ID, TICKER, Operation.SELL, sellQuantity, TRADE_DATE);

        assertThat(saved.operation()).isEqualTo(Operation.SELL);
        verify(repository).save(any());
    }

    @Test
    void record_buyNeverChecksLaterSells() {
        when(symbolLookup.exists(TICKER)).thenReturn(true);
        when(symbolLookup.isDelisted(TICKER)).thenReturn(false);
        when(repository.holdingsAsOf(USER_ID, TICKER, TODAY)).thenReturn(QUANTITY.value());

        recordTransaction.record(USER_ID, TICKER, Operation.BUY, QUANTITY, TRADE_DATE);

        verify(repository, never()).findAllAfter(any(), any(), any());
    }

    private static Transaction laterTransaction(Operation operation, String quantity, LocalDate tradeDate) {
        return new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()), USER_ID, TICKER, operation,
                new Quantity(new BigDecimal(quantity)), tradeDate, FIXED_NOW, FIXED_NOW);
    }
}
