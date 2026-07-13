package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcPriceHistoryRepositoryIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final BigDecimal CLOSE = new BigDecimal("150.00");

    @Autowired
    private PriceHistoryRepository priceHistory;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedSymbol() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
    }

    @Test
    void upsertBatch_emptyList_returnsZeroAndInsertsNothing() {
        int rowsAffected = priceHistory.upsertBatch(Collections.emptyList());

        assertThat(rowsAffected).isZero();
        assertThat(countRows()).isZero();
    }

    @Test
    void upsertBatch_singleRow_insertsIt() {
        LocalDate tradeDate = LocalDate.of(2026, 6, 15);
        int rowsAffected = priceHistory.upsertBatch(List.of(price(tradeDate, CLOSE)));

        assertThat(rowsAffected).isEqualTo(1);
        assertThat(readClose(tradeDate)).isEqualByComparingTo(CLOSE);
    }

    @Test
    void upsertBatch_inputLargerThanBatchSize_spansMultipleChunksAndInsertsAll() {
        int totalRows = 2500;
        List<PriceHistory> prices = pricesFor(LocalDate.of(2020, 1, 1), totalRows);

        int rowsAffected = priceHistory.upsertBatch(prices);

        assertThat(rowsAffected).isEqualTo(totalRows);
        assertThat(countRows()).isEqualTo(totalRows);
    }

    @Test
    void upsertBatch_reRunSameBatch_isIdempotentAndRefreshesUpdatedAt() {
        LocalDate tradeDate = LocalDate.of(2026, 6, 15);
        List<PriceHistory> initial = List.of(price(tradeDate, CLOSE));
        priceHistory.upsertBatch(initial);
        final Instant firstUpdatedAt = readUpdatedAt(tradeDate);

        waitForPostgresClockToAdvance();
        priceHistory.upsertBatch(initial);

        assertThat(countRows()).isEqualTo(1);
        assertThat(readUpdatedAt(tradeDate)).isAfter(firstUpdatedAt);
    }

    @Test
    void upsertBatch_conflictingClosePrice_overwritesExistingValue() {
        LocalDate tradeDate = LocalDate.of(2026, 6, 15);
        priceHistory.upsertBatch(List.of(price(tradeDate, new BigDecimal("100.00"))));

        BigDecimal correctedClose = new BigDecimal("101.25");
        priceHistory.upsertBatch(List.of(price(tradeDate, correctedClose)));

        assertThat(readClose(tradeDate)).isEqualByComparingTo(correctedClose);
    }

    private PriceHistory price(LocalDate tradeDate, BigDecimal closePrice) {
        return new PriceHistory(AAPL, tradeDate, closePrice, true, NOW, NOW);
    }

    private List<PriceHistory> pricesFor(LocalDate startDate, int count) {
        List<PriceHistory> prices = new ArrayList<>(count);
        for (int day = 0; day < count; day++) {
            prices.add(price(startDate.plusDays(day), CLOSE));
        }
        return prices;
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_history WHERE ticker = ?", Integer.class, AAPL.value());
        return count == null ? 0 : count;
    }

    private BigDecimal readClose(LocalDate tradeDate) {
        return jdbcTemplate.queryForObject(
                "SELECT close_price FROM price_history WHERE ticker = ? AND trade_date = ?",
                BigDecimal.class, AAPL.value(), tradeDate);
    }

    private Instant readUpdatedAt(LocalDate tradeDate) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM price_history WHERE ticker = ? AND trade_date = ?",
                Instant.class, AAPL.value(), tradeDate);
    }

    // Postgres now() has microsecond resolution; a 2ms pause guarantees a distinct timestamp on the
    // second upsert so we can prove updated_at actually advanced.
    private static void waitForPostgresClockToAdvance() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while spacing upserts", ex);
        }
    }
}
