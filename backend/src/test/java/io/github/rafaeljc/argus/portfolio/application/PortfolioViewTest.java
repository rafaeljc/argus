package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioViewTest {

    private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-06-10");
    private static final Money TOTAL_VALUE = new Money(new BigDecimal("1875.00"));

    private static Position pricedPosition() {
        return new Position(
                new Ticker("AAPL"),
                new Quantity(new BigDecimal("10")),
                new BigDecimal("187.500000"),
                AS_OF_DATE,
                TOTAL_VALUE,
                new BigDecimal("100.00"),
                false,
                false,
                null);
    }

    @Test
    void constructor_validInput_setsAllFields() {
        List<Position> positions = List.of(pricedPosition());

        PortfolioView view = new PortfolioView(AS_OF_DATE, TOTAL_VALUE, false, positions);

        assertThat(view.asOfDate()).isEqualTo(AS_OF_DATE);
        assertThat(view.totalValue()).isEqualTo(TOTAL_VALUE);
        assertThat(view.totalValuePending()).isFalse();
        assertThat(view.positions()).containsExactly(pricedPosition());
    }

    @Test
    void constructor_positionsList_isDefensivelyCopied() {
        List<Position> mutable = new ArrayList<>(List.of(pricedPosition()));

        PortfolioView view = new PortfolioView(AS_OF_DATE, TOTAL_VALUE, false, mutable);
        mutable.clear();

        assertThat(view.positions()).hasSize(1);
    }

    @Test
    void constructor_nullAsOfDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PortfolioView(null, TOTAL_VALUE, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asOfDate");
    }

    @Test
    void constructor_nullTotalValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PortfolioView(AS_OF_DATE, null, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalValue");
    }

    @Test
    void constructor_nullPositions_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PortfolioView(AS_OF_DATE, TOTAL_VALUE, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positions");
    }
}
