package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SnapshotValuationTest {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");

    @Test
    void valueFor_noHeldTickers_returnsZeroMoney() {
        Optional<Money> result = SnapshotValuation.valueFor(Map.of(), Map.of());

        assertThat(result).contains(new Money(BigDecimal.ZERO));
    }

    @Test
    void valueFor_allHeldTickersHaveCloses_returnsSummedTotal() {
        Map<Ticker, BigDecimal> held = Map.of(AAPL, new BigDecimal("10"), MSFT, new BigDecimal("2"));
        Map<Ticker, BigDecimal> closes = Map.of(AAPL, new BigDecimal("150.00"), MSFT, new BigDecimal("420.75"));

        Optional<Money> result = SnapshotValuation.valueFor(held, closes);

        // 10 * 150.00 + 2 * 420.75 = 1500.00 + 841.50 = 2341.50
        assertThat(result).contains(new Money(new BigDecimal("2341.50")));
    }

    @Test
    void valueFor_oneHeldTickerMissingClose_returnsEmpty() {
        Map<Ticker, BigDecimal> held = Map.of(AAPL, new BigDecimal("10"), MSFT, new BigDecimal("2"));
        Map<Ticker, BigDecimal> closes = Map.of(AAPL, new BigDecimal("150.00"));

        Optional<Money> result = SnapshotValuation.valueFor(held, closes);

        assertThat(result).isEmpty();
    }
}
