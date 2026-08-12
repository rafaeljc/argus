package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MassiveResponseMapperTest {

    private static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");

    // 2026-03-10T00:00Z is still 2026-03-09 in America/New_York. Daily bars must be dated in
    // exchange-local time, so this timestamp separates a correct mapping from a UTC one.
    private static final long MIDNIGHT_UTC_MARCH_10 = Instant.parse("2026-03-10T00:00:00Z").toEpochMilli();

    private final MassiveResponseMapper mapper = new MassiveResponseMapper();

    @Test
    void toClosesForTicker_validAggregate_mapsToSplitAdjustedPriceHistory() {
        List<MassiveAggregate> aggregates =
                List.of(new MassiveAggregate(null, new BigDecimal("123.45"), MIDNIGHT_UTC_MARCH_10));

        List<PriceHistory> result = mapper.toClosesForTicker(aggregates, new Ticker("AAPL"), NOW);

        assertThat(result).containsExactly(new PriceHistory(
                new Ticker("AAPL"), LocalDate.of(2026, 3, 9), new BigDecimal("123.45"), true, NOW, NOW));
    }

    @Test
    void toClosesForTicker_nonPositiveClose_skipsRow() {
        List<MassiveAggregate> aggregates = List.of(
                new MassiveAggregate(null, BigDecimal.ZERO, MIDNIGHT_UTC_MARCH_10),
                new MassiveAggregate(null, new BigDecimal("10.00"), MIDNIGHT_UTC_MARCH_10));

        List<PriceHistory> result = mapper.toClosesForTicker(aggregates, new Ticker("AAPL"), NOW);

        assertThat(result).extracting(PriceHistory::closePrice).containsExactly(new BigDecimal("10.00"));
    }

    @Test
    void toClosesForTicker_nullClose_skipsRow() {
        List<MassiveAggregate> aggregates = List.of(new MassiveAggregate(null, null, MIDNIGHT_UTC_MARCH_10));

        List<PriceHistory> result = mapper.toClosesForTicker(aggregates, new Ticker("AAPL"), NOW);

        assertThat(result).isEmpty();
    }

    @Test
    void toClosesForTicker_emptyResults_returnsEmptyList() {
        List<PriceHistory> result = mapper.toClosesForTicker(List.of(), new Ticker("AAPL"), NOW);

        assertThat(result).isEmpty();
    }

    @Test
    void toClosesForTickers_requestedTicker_mapsUsingPerRowTicker() {
        List<MassiveAggregate> aggregates = List.of(
                new MassiveAggregate("AAPL", new BigDecimal("123.45"), MIDNIGHT_UTC_MARCH_10),
                new MassiveAggregate("MSFT", new BigDecimal("400.00"), MIDNIGHT_UTC_MARCH_10));

        List<PriceHistory> result =
                mapper.toClosesForTickers(aggregates, Set.of(new Ticker("AAPL"), new Ticker("MSFT")), NOW);

        assertThat(result)
                .extracting(PriceHistory::ticker, PriceHistory::tradeDate)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(new Ticker("AAPL"), LocalDate.of(2026, 3, 9)),
                        org.assertj.core.groups.Tuple.tuple(new Ticker("MSFT"), LocalDate.of(2026, 3, 9)));
    }

    @Test
    void toClosesForTickers_unrequestedTicker_isFilteredOut() {
        List<MassiveAggregate> aggregates = List.of(
                new MassiveAggregate("AAPL", new BigDecimal("123.45"), MIDNIGHT_UTC_MARCH_10),
                new MassiveAggregate("MSFT", new BigDecimal("400.00"), MIDNIGHT_UTC_MARCH_10));

        List<PriceHistory> result = mapper.toClosesForTickers(aggregates, Set.of(new Ticker("AAPL")), NOW);

        assertThat(result).extracting(PriceHistory::ticker).containsExactly(new Ticker("AAPL"));
    }

    // The grouped endpoint returns the whole US market, including warrants and unit classes whose
    // symbols ("BRK.B" is fine, "RILYL-W" is not) fall outside Ticker's pattern. One such row must
    // not abort the whole response.
    @Test
    void toClosesForTickers_symbolOutsideTickerPattern_skipsRowInsteadOfThrowing() {
        List<MassiveAggregate> aggregates = List.of(
                new MassiveAggregate("RILYL-W", new BigDecimal("1.00"), MIDNIGHT_UTC_MARCH_10),
                new MassiveAggregate("AAPL", new BigDecimal("123.45"), MIDNIGHT_UTC_MARCH_10));

        List<PriceHistory> result = mapper.toClosesForTickers(aggregates, Set.of(new Ticker("AAPL")), NOW);

        assertThat(result).extracting(PriceHistory::ticker).containsExactly(new Ticker("AAPL"));
    }

    @Test
    void toSymbols_nyseAndNasdaqMics_mapToExchanges() {
        List<MassiveTicker> tickers = List.of(
                new MassiveTicker("AAPL", "Apple Inc.", "XNAS"), new MassiveTicker("KO", "Coca-Cola Co", "XNYS"));

        Set<Symbol> result = mapper.toSymbols(tickers, NOW);

        assertThat(result)
                .containsExactlyInAnyOrder(
                        new Symbol(new Ticker("AAPL"), Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW),
                        new Symbol(new Ticker("KO"), Exchange.NYSE, "Coca-Cola Co", false, NOW, NOW, NOW));
    }

    @Test
    void toSymbols_unsupportedMic_isDropped() {
        List<MassiveTicker> tickers =
                List.of(new MassiveTicker("XYZ", "Some OTC Co", "OTCM"), new MassiveTicker("AAPL", "Apple", "XNAS"));

        Set<Symbol> result = mapper.toSymbols(tickers, NOW);

        assertThat(result).extracting(Symbol::ticker).containsExactly(new Ticker("AAPL"));
    }

    @Test
    void toSymbols_symbolOutsideTickerPattern_isDropped() {
        List<MassiveTicker> tickers =
                List.of(new MassiveTicker("RILYL-W", "B. Riley Warrant", "XNAS"), new MassiveTicker("AAPL", "Apple",
                        "XNAS"));

        Set<Symbol> result = mapper.toSymbols(tickers, NOW);

        assertThat(result).extracting(Symbol::ticker).containsExactly(new Ticker("AAPL"));
    }

    @Test
    void toSymbols_nullMic_isDropped() {
        List<MassiveTicker> tickers = List.of(new MassiveTicker("AAPL", "Apple", null));

        Set<Symbol> result = mapper.toSymbols(tickers, NOW);

        assertThat(result).isEmpty();
    }

    @Test
    void toSymbols_emptyResults_returnsEmptySet() {
        assertThat(mapper.toSymbols(List.of(), NOW)).isEmpty();
    }
}
