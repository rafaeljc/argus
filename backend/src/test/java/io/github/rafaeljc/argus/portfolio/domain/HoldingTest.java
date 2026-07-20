package io.github.rafaeljc.argus.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HoldingTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final Quantity QUANTITY = new Quantity(new BigDecimal("10"));
    private static final Instant UPDATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    private static Holding newHolding() {
        return new Holding(USER_ID, TICKER, QUANTITY, UPDATED_AT);
    }

    @Test
    void constructor_validInput_setsAllFields() {
        Holding holding = newHolding();

        assertThat(holding.userId()).isEqualTo(USER_ID);
        assertThat(holding.ticker()).isEqualTo(TICKER);
        assertThat(holding.quantity()).isEqualTo(QUANTITY);
        assertThat(holding.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Holding(null, TICKER, QUANTITY, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void constructor_nullTicker_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Holding(USER_ID, null, QUANTITY, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticker");
    }

    @Test
    void constructor_nullQuantity_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Holding(USER_ID, TICKER, null, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void constructor_nullUpdatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new Holding(USER_ID, TICKER, QUANTITY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }
}
