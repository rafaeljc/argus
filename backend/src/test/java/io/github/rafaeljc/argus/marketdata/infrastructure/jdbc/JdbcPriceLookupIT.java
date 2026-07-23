package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcPriceLookupIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GOOG = new Ticker("GOOG");
    private static final Ticker UNKNOWN = new Ticker("ZZZZ");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate D1 = LocalDate.of(2026, 6, 12);
    private static final LocalDate D2 = LocalDate.of(2026, 6, 13);
    private static final LocalDate D3 = LocalDate.of(2026, 6, 15);

    @Autowired
    private PriceLookup lookup;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private PriceHistoryRepository prices;

    @Test
    void latestClose_singleRow_returnsIt() {
        seed(AAPL);
        prices.upsertBatch(List.of(priceOn(AAPL, D2, "150.00")));

        Optional<PriceLookup.Close> latest = lookup.latestClose(AAPL);

        assertThat(latest).hasValueSatisfying(close -> {
            assertThat(close.ticker()).isEqualTo(AAPL);
            assertThat(close.tradeDate()).isEqualTo(D2);
            assertThat(close.price()).isEqualByComparingTo("150.00");
        });
    }

    @Test
    void latestClose_multipleTradeDates_returnsMostRecent() {
        seed(AAPL);
        prices.upsertBatch(List.of(
                priceOn(AAPL, D1, "148.00"),
                priceOn(AAPL, D3, "152.50"),
                priceOn(AAPL, D2, "150.00")));

        Optional<PriceLookup.Close> latest = lookup.latestClose(AAPL);

        assertThat(latest).hasValueSatisfying(close -> {
            assertThat(close.ticker()).isEqualTo(AAPL);
            assertThat(close.tradeDate()).isEqualTo(D3);
            assertThat(close.price()).isEqualByComparingTo("152.50");
        });
    }

    @Test
    void latestClose_noRows_returnsEmpty() {
        seed(AAPL);

        assertThat(lookup.latestClose(AAPL)).isEmpty();
    }

    @Test
    void latestClose_unknownTicker_returnsEmpty() {
        assertThat(lookup.latestClose(UNKNOWN)).isEmpty();
    }

    @Test
    void closeOn_existingRow_returnsClose() {
        seed(AAPL);
        prices.upsertBatch(List.of(priceOn(AAPL, D2, "150.00")));

        assertThat(lookup.closeOn(AAPL, D2)).hasValueSatisfying(v ->
                assertThat(v).isEqualByComparingTo("150.00"));
    }

    @Test
    void closeOn_dateWithoutRow_returnsEmpty() {
        seed(AAPL);
        prices.upsertBatch(List.of(priceOn(AAPL, D1, "148.00")));

        assertThat(lookup.closeOn(AAPL, D2)).isEmpty();
    }

    @Test
    void closesOn_emptyInput_returnsEmptyMap() {
        assertThat(lookup.closesOn(Set.of(), D2)).isEmpty();
    }

    @Test
    void closesOn_singleTicker_returnsSingletonMap() {
        seed(AAPL);
        prices.upsertBatch(List.of(priceOn(AAPL, D2, "150.00")));

        Map<Ticker, BigDecimal> result = lookup.closesOn(Set.of(AAPL), D2);

        assertThat(result).hasSize(1);
        assertThat(result.get(AAPL)).isEqualByComparingTo("150.00");
    }

    @Test
    void closesOn_multipleTickersSomeMissingOnDate_returnsOnlyFound() {
        seed(AAPL);
        seed(MSFT);
        seed(GOOG);
        prices.upsertBatch(List.of(
                priceOn(AAPL, D2, "150.00"),
                priceOn(MSFT, D2, "420.75"),
                // GOOG has a close on D1 but not D2
                priceOn(GOOG, D1, "175.10")));

        Map<Ticker, BigDecimal> result = lookup.closesOn(Set.of(AAPL, MSFT, GOOG, UNKNOWN), D2);

        assertThat(result).hasSize(2);
        assertThat(result.get(AAPL)).isEqualByComparingTo("150.00");
        assertThat(result.get(MSFT)).isEqualByComparingTo("420.75");
        assertThat(result).doesNotContainKey(GOOG);
        assertThat(result).doesNotContainKey(UNKNOWN);
    }

    @Test
    void closesOn_dateWithNoRows_returnsEmptyMap() {
        seed(AAPL);
        prices.upsertBatch(List.of(priceOn(AAPL, D1, "148.00")));

        assertThat(lookup.closesOn(Set.of(AAPL), D2)).isEmpty();
    }

    @Test
    void latestCloses_emptyInput_returnsEmptyMap() {
        assertThat(lookup.latestCloses(Set.of())).isEmpty();
    }

    @Test
    void latestCloses_multipleTradeDatesPerTicker_returnsMostRecentEach() {
        seed(AAPL);
        seed(MSFT);
        prices.upsertBatch(List.of(
                priceOn(AAPL, D1, "148.00"),
                priceOn(AAPL, D3, "152.50"),
                priceOn(AAPL, D2, "150.00"),
                priceOn(MSFT, D2, "420.75")));

        Map<Ticker, PriceLookup.Close> result = lookup.latestCloses(Set.of(AAPL, MSFT, UNKNOWN));

        assertThat(result).hasSize(2);
        assertThat(result.get(AAPL).tradeDate()).isEqualTo(D3);
        assertThat(result.get(AAPL).price()).isEqualByComparingTo("152.50");
        assertThat(result.get(MSFT).tradeDate()).isEqualTo(D2);
        assertThat(result.get(MSFT).price()).isEqualByComparingTo("420.75");
        assertThat(result).doesNotContainKey(UNKNOWN);
    }

    @Test
    void latestCloses_tickerWithNoRows_isAbsentFromResult() {
        seed(AAPL);
        seed(MSFT);
        prices.upsertBatch(List.of(priceOn(AAPL, D2, "150.00")));

        Map<Ticker, PriceLookup.Close> result = lookup.latestCloses(Set.of(AAPL, MSFT));

        assertThat(result).hasSize(1);
        assertThat(result).doesNotContainKey(MSFT);
    }

    private void seed(Ticker ticker) {
        symbols.save(new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", false, NOW, NOW, NOW));
    }

    private static PriceHistory priceOn(Ticker ticker, LocalDate tradeDate, String close) {
        return new PriceHistory(ticker, tradeDate, new BigDecimal(close), true, NOW, NOW);
    }
}
