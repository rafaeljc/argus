package io.github.rafaeljc.argus.portfolio.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.portfolio.application.PortfolioView;
import io.github.rafaeljc.argus.portfolio.application.Position;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioViewResponseTest {

    private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-06-10");

    @Test
    void from_pricedPosition_projectsAllFields() {
        Position priced = new Position(
                new Ticker("AAPL"),
                new Quantity(new BigDecimal("10")),
                new BigDecimal("187.500000"),
                AS_OF_DATE,
                new Money(new BigDecimal("1875.00")),
                new BigDecimal("15.19"),
                false,
                false,
                null);
        PortfolioView view =
                new PortfolioView(AS_OF_DATE, new Money(new BigDecimal("12345.67")), false, List.of(priced));

        PortfolioViewResponse response = PortfolioViewResponse.from(view);

        assertThat(response.asOfDate()).isEqualTo(AS_OF_DATE);
        assertThat(response.totalValue()).isEqualTo("12345.67");
        assertThat(response.totalValuePending()).isFalse();
        assertThat(response.positions()).hasSize(1);

        PortfolioViewResponse.PositionResponse position = response.positions().get(0);
        assertThat(position.ticker()).isEqualTo("AAPL");
        assertThat(position.quantity()).isEqualTo("10.000000");
        assertThat(position.lastClosePrice()).isEqualTo("187.500000");
        assertThat(position.lastCloseDate()).isEqualTo(AS_OF_DATE);
        assertThat(position.positionValue()).isEqualTo("1875.00");
        assertThat(position.percentOfPortfolio()).isEqualByComparingTo("15.19");
        assertThat(position.pricePending()).isFalse();
        assertThat(position.priceStale()).isFalse();
        assertThat(position.staleSince()).isNull();
    }

    @Test
    void from_pendingPosition_projectsNullPriceFields() {
        Position pending = new Position(
                new Ticker("ZZZZ"), new Quantity(new BigDecimal("5")), null, null, null, null, true, false, null);
        PortfolioView view = new PortfolioView(AS_OF_DATE, new Money(BigDecimal.ZERO), true, List.of(pending));

        PortfolioViewResponse response = PortfolioViewResponse.from(view);

        PortfolioViewResponse.PositionResponse position = response.positions().get(0);
        assertThat(position.lastClosePrice()).isNull();
        assertThat(position.lastCloseDate()).isNull();
        assertThat(position.positionValue()).isNull();
        assertThat(position.percentOfPortfolio()).isNull();
        assertThat(position.pricePending()).isTrue();
    }

    @Test
    void from_staleDelistedPosition_projectsStaleSinceDate() {
        LocalDate staleSince = LocalDate.parse("2026-05-01");
        Position stale = new Position(
                new Ticker("GE"),
                new Quantity(new BigDecimal("10")),
                new BigDecimal("50.00"),
                staleSince,
                new Money(new BigDecimal("500.00")),
                new BigDecimal("100.00"),
                false,
                true,
                staleSince);
        PortfolioView view = new PortfolioView(AS_OF_DATE, new Money(new BigDecimal("500.00")), false, List.of(stale));

        PortfolioViewResponse response = PortfolioViewResponse.from(view);

        PortfolioViewResponse.PositionResponse position = response.positions().get(0);
        assertThat(position.priceStale()).isTrue();
        assertThat(position.staleSince()).isEqualTo(staleSince);
    }
}
