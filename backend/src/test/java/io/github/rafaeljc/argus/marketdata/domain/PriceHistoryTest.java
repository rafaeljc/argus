package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PriceHistoryTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 15);
    private static final Instant NOW = Instant.parse("2026-06-15T23:00:00Z");
    private static final BigDecimal CLOSE = new BigDecimal("192.55");

    @Test
    void constructor_validInputs_isAllowed() {
        PriceHistory price = new PriceHistory(AAPL, TRADE_DATE, CLOSE, true, NOW, NOW);

        assertThat(price.ticker()).isEqualTo(AAPL);
        assertThat(price.tradeDate()).isEqualTo(TRADE_DATE);
        assertThat(price.closePrice()).isEqualByComparingTo(CLOSE);
        assertThat(price.isSplitAdjusted()).isTrue();
        assertThat(price.createdAt()).isEqualTo(NOW);
        assertThat(price.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void constructor_nullTicker_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(null, TRADE_DATE, CLOSE, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullTradeDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(AAPL, null, CLOSE, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullClosePrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(AAPL, TRADE_DATE, null, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_zeroClosePrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(AAPL, TRADE_DATE, BigDecimal.ZERO, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeClosePrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(AAPL, TRADE_DATE, new BigDecimal("-0.01"), true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(AAPL, TRADE_DATE, CLOSE, true, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullUpdatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PriceHistory(AAPL, TRADE_DATE, CLOSE, true, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_updatedAtBeforeCreatedAt_throwsIllegalArgument() {
        Instant earlier = NOW.minusSeconds(1);

        assertThatThrownBy(() -> new PriceHistory(AAPL, TRADE_DATE, CLOSE, true, NOW, earlier))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
