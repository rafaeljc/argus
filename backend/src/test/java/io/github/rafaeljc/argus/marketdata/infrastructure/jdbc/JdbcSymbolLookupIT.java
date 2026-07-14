package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcSymbolLookupIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GOOG = new Ticker("GOOG");
    private static final Ticker UNKNOWN = new Ticker("ZZZZ");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Autowired
    private SymbolLookup lookup;

    @Autowired
    private SymbolRepository symbols;

    @Test
    void exists_knownTicker_returnsTrue() {
        symbols.save(activeSymbol(AAPL));

        assertThat(lookup.exists(AAPL)).isTrue();
    }

    @Test
    void exists_unknownTicker_returnsFalse() {
        assertThat(lookup.exists(UNKNOWN)).isFalse();
    }

    @Test
    void isDelisted_delistedTicker_returnsTrue() {
        symbols.save(delistedSymbol(AAPL));

        assertThat(lookup.isDelisted(AAPL)).isTrue();
    }

    @Test
    void isDelisted_activeTicker_returnsFalse() {
        symbols.save(activeSymbol(AAPL));

        assertThat(lookup.isDelisted(AAPL)).isFalse();
    }

    @Test
    void isDelisted_unknownTicker_returnsFalse() {
        assertThat(lookup.isDelisted(UNKNOWN)).isFalse();
    }

    @Test
    void delistedAmong_emptyInput_returnsEmptySet() {
        assertThat(lookup.delistedAmong(Set.of())).isEmpty();
    }

    @Test
    void delistedAmong_singleDelistedTicker_returnsIt() {
        symbols.save(delistedSymbol(AAPL));

        assertThat(lookup.delistedAmong(Set.of(AAPL))).containsExactly(AAPL);
    }

    @Test
    void delistedAmong_mixOfDelistedAndActiveAndUnknown_returnsOnlyDelisted() {
        symbols.save(delistedSymbol(AAPL));
        symbols.save(activeSymbol(MSFT));
        // GOOG is delisted, UNKNOWN is not in DB at all
        symbols.save(delistedSymbol(GOOG));

        Set<Ticker> result = lookup.delistedAmong(Set.of(AAPL, MSFT, GOOG, UNKNOWN));

        assertThat(result).containsExactlyInAnyOrder(AAPL, GOOG);
    }

    @Test
    void delistedAmong_noneDelisted_returnsEmptySet() {
        symbols.save(activeSymbol(AAPL));
        symbols.save(activeSymbol(MSFT));

        assertThat(lookup.delistedAmong(Set.of(AAPL, MSFT))).isEmpty();
    }

    private static Symbol activeSymbol(Ticker ticker) {
        return new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", false, NOW, NOW, NOW);
    }

    private static Symbol delistedSymbol(Ticker ticker) {
        return new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", true, NOW, NOW, NOW);
    }
}
