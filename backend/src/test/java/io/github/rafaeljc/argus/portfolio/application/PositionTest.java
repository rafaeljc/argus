package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static final Ticker TICKER = new Ticker("AAPL");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("10"));

    @Test
    void constructor_validPricedPosition_setsAllFields() {
        Position position = new Position(
                TICKER,
                QUANTITY,
                new BigDecimal("187.500000"),
                LocalDate.parse("2026-06-10"),
                new Money(new BigDecimal("1875.00")),
                new BigDecimal("15.19"),
                false,
                false,
                null);

        assertThat(position.ticker()).isEqualTo(TICKER);
        assertThat(position.quantity()).isEqualTo(QUANTITY);
        assertThat(position.lastClosePrice()).isEqualByComparingTo("187.500000");
        assertThat(position.lastCloseDate()).isEqualTo(LocalDate.parse("2026-06-10"));
        assertThat(position.positionValue()).isEqualTo(new Money(new BigDecimal("1875.00")));
        assertThat(position.percentOfPortfolio()).isEqualByComparingTo("15.19");
        assertThat(position.pricePending()).isFalse();
        assertThat(position.priceStale()).isFalse();
        assertThat(position.staleSince()).isNull();
    }

    @Test
    void constructor_pendingPosition_allowsNullPriceFields() {
        Position position = new Position(TICKER, QUANTITY, null, null, null, null, true, false, null);

        assertThat(position.pricePending()).isTrue();
        assertThat(position.lastClosePrice()).isNull();
        assertThat(position.positionValue()).isNull();
        assertThat(position.percentOfPortfolio()).isNull();
    }

    @Test
    void constructor_nullTicker_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Position(null, QUANTITY, null, null, null, null, true, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void constructor_nullQuantity_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Position(TICKER, null, null, null, null, null, true, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }
}
