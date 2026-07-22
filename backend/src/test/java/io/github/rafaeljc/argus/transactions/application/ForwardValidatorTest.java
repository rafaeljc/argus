package io.github.rafaeljc.argus.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForwardValidatorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final LocalDate START_DATE = LocalDate.parse("2026-06-15");
    private static final LocalDate CUTOFF = START_DATE.minusDays(1);

    @Mock
    private TransactionRepository repository;

    private ForwardValidator forwardValidator;

    @BeforeEach
    void setUp() {
        forwardValidator = new ForwardValidator(repository);
    }

    @Test
    void firstOversoldSell_noTransactionsFromStartDate_returnsEmpty() {
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(BigDecimal.ZERO);
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF)).thenReturn(List.of());

        Optional<ForwardValidator.Oversold> result = forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void firstOversoldSell_sellWithinHolding_returnsEmpty() {
        Transaction sell = transaction(Operation.SELL, "10", START_DATE);
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(new BigDecimal("20"));
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF)).thenReturn(List.of(sell));

        Optional<ForwardValidator.Oversold> result = forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void firstOversoldSell_sellExactlyExhaustsHolding_returnsEmpty() {
        Transaction sell = transaction(Operation.SELL, "20", START_DATE);
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(new BigDecimal("20"));
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF)).thenReturn(List.of(sell));

        Optional<ForwardValidator.Oversold> result = forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void firstOversoldSell_sellExceedsHolding_returnsSellWithHeldBefore() {
        Transaction sell = transaction(Operation.SELL, "10", START_DATE);
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(new BigDecimal("5"));
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF)).thenReturn(List.of(sell));

        Optional<ForwardValidator.Oversold> result = forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        assertThat(result).isPresent();
        assertThat(result.get().sell()).isEqualTo(sell);
        assertThat(result.get().heldBefore()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void firstOversoldSell_interveningLaterBuyCoversLaterSell_returnsEmpty() {
        LocalDate buyDate = START_DATE.plusDays(2);
        LocalDate laterSellDate = START_DATE.plusDays(5);
        Transaction sell = transaction(Operation.SELL, "5", START_DATE);
        Transaction laterBuy = transaction(Operation.BUY, "10", buyDate);
        Transaction laterSell = transaction(Operation.SELL, "8", laterSellDate);
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(new BigDecimal("5"));
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF))
                .thenReturn(List.of(sell, laterBuy, laterSell));

        Optional<ForwardValidator.Oversold> result = forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    void firstOversoldSell_secondSellOversold_returnsSecondSellNotFirst() {
        LocalDate secondSellDate = START_DATE.plusDays(5);
        Transaction firstSell = transaction(Operation.SELL, "5", START_DATE);
        Transaction secondSell = transaction(Operation.SELL, "10", secondSellDate);
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(new BigDecimal("10"));
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF))
                .thenReturn(List.of(firstSell, secondSell));

        Optional<ForwardValidator.Oversold> result = forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        assertThat(result).isPresent();
        assertThat(result.get().sell()).isEqualTo(secondSell);
        assertThat(result.get().heldBefore()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void firstOversoldSell_readsStartBalanceStrictlyBeforeStartDate() {
        when(repository.holdingsAsOf(USER_ID, TICKER, CUTOFF)).thenReturn(BigDecimal.ZERO);
        when(repository.findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF)).thenReturn(List.of());

        forwardValidator.firstOversoldSell(USER_ID, TICKER, START_DATE);

        verify(repository).holdingsAsOf(USER_ID, TICKER, CUTOFF);
        verify(repository).findAllAfterOrderedByTradeDateThenCreatedAt(USER_ID, TICKER, CUTOFF);
    }

    private static Transaction transaction(Operation operation, String quantity, LocalDate tradeDate) {
        return new Transaction(
                new TransactionId(UuidCreator.getTimeOrderedEpoch()),
                USER_ID,
                TICKER,
                operation,
                new Quantity(new BigDecimal(quantity)),
                tradeDate,
                FIXED_NOW,
                FIXED_NOW);
    }
}
