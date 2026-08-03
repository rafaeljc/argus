package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcSymbolRepositoryIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private SymbolRepository symbols;

    @Test
    void save_thenFindByTicker_roundTripsAllFields() {
        Symbol saved = symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));

        Optional<Symbol> found = symbols.findByTicker(AAPL);

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    @Test
    void save_delistedSymbol_persistsFlagAndVendorCheck() {
        symbols.save(new Symbol(new Ticker("XOM"), Exchange.NYSE, "Exxon", true, NOW, NOW, NOW));

        Symbol found = symbols.findByTicker(new Ticker("XOM")).orElseThrow();

        assertThat(found.isDelisted()).isTrue();
        assertThat(found.lastVendorCheck()).isEqualTo(NOW);
    }

    @Test
    void save_symbolWithNullOptionals_persistsSuccessfully() {
        symbols.save(new Symbol(new Ticker("BRK.B"), Exchange.NYSE, null, false, null, NOW, NOW));

        Symbol found = symbols.findByTicker(new Ticker("BRK.B")).orElseThrow();

        assertThat(found.name()).isNull();
        assertThat(found.lastVendorCheck()).isNull();
    }

    @Test
    void findByTicker_missing_returnsEmpty() {
        assertThat(symbols.findByTicker(new Ticker("NONE"))).isEmpty();
    }

    @Test
    void deleteByTicker_removesRow() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple", false, NOW, NOW, NOW));
        assertThat(symbols.findByTicker(AAPL)).isPresent();

        symbols.deleteByTicker(AAPL);

        assertThat(symbols.findByTicker(AAPL)).isEmpty();
    }

    @Test
    void upsertAll_newTickers_insertsThemActiveWithVendorCheck() {
        Symbol vendorAapl = new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW);
        Symbol vendorMsft = new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, NOW, NOW, NOW);

        int upserted = symbols.upsertAll(List.of(vendorAapl, vendorMsft), NOW);

        assertThat(upserted).isEqualTo(2);
        Symbol found = symbols.findByTicker(AAPL).orElseThrow();
        assertThat(found.isDelisted()).isFalse();
        assertThat(found.lastVendorCheck()).isEqualTo(NOW);
        assertThat(found.name()).isEqualTo("Apple Inc.");
    }

    @Test
    void upsertAll_existingDelistedTicker_relistsAndTouchesVendorCheck() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", true, NOW.minusSeconds(3600), NOW, NOW));
        Symbol vendorAapl = new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW);

        symbols.upsertAll(List.of(vendorAapl), NOW);

        Symbol found = symbols.findByTicker(AAPL).orElseThrow();
        assertThat(found.isDelisted()).isFalse();
        assertThat(found.lastVendorCheck()).isEqualTo(NOW);
    }

    @Test
    void upsertAll_emptyCollection_isNoOp() {
        int upserted = symbols.upsertAll(List.of(), NOW);

        assertThat(upserted).isZero();
    }

    @Test
    void markDelistedIfNotSyncedAt_tickerNotTouchedByUpsert_flipsToDelisted() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW.minusSeconds(3600), NOW, NOW));
        symbols.upsertAll(
                List.of(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, NOW, NOW, NOW)), NOW);

        int delisted = symbols.markDelistedIfNotSyncedAt(NOW);

        assertThat(delisted).isEqualTo(1);
        Symbol found = symbols.findByTicker(AAPL).orElseThrow();
        assertThat(found.isDelisted()).isTrue();
    }

    @Test
    void markDelistedIfNotSyncedAt_tickerTouchedByUpsert_staysActive() {
        symbols.upsertAll(List.of(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW)), NOW);

        symbols.markDelistedIfNotSyncedAt(NOW);

        Symbol found = symbols.findByTicker(AAPL).orElseThrow();
        assertThat(found.isDelisted()).isFalse();
    }

    @Test
    void markDelistedIfNotSyncedAt_alreadyDelistedTicker_isNotCountedAgain() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", true, NOW.minusSeconds(3600), NOW, NOW));

        int delisted = symbols.markDelistedIfNotSyncedAt(NOW);

        assertThat(delisted).isZero();
    }

    @Test
    void markDelistedIfNotSyncedAt_tickerNeverSynced_flipsToDelisted() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, null, NOW, NOW));

        int delisted = symbols.markDelistedIfNotSyncedAt(NOW);

        assertThat(delisted).isEqualTo(1);
    }
}
