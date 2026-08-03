package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSymbolRepository implements SymbolRepository {

    // Same batching rationale as JdbcPriceHistoryRepository: keeps each round-trip well under
    // Postgres' 65,535 parameter limit while amortising network overhead across a full vendor
    // universe sync (~6,000 US-listed tickers).
    private static final int BATCH_SIZE = 1000;

    private static final String SAVE_SQL =
            """
            INSERT INTO symbols
                (ticker, exchange, name, is_delisted, last_vendor_check, created_at, updated_at)
            VALUES
                (:ticker, :exchange, :name, :isDelisted, :lastVendorCheck, :createdAt, :updatedAt)
            ON CONFLICT (ticker) DO UPDATE
               SET exchange          = EXCLUDED.exchange,
                   name              = EXCLUDED.name,
                   is_delisted       = EXCLUDED.is_delisted,
                   last_vendor_check = EXCLUDED.last_vendor_check,
                   updated_at        = EXCLUDED.updated_at
            """;

    private static final String UPSERT_SQL =
            """
            INSERT INTO symbols
                (ticker, exchange, name, is_delisted, last_vendor_check, created_at, updated_at)
            VALUES
                (:ticker, :exchange, :name, FALSE, :asOf, :asOf, :asOf)
            ON CONFLICT (ticker) DO UPDATE
               SET exchange          = EXCLUDED.exchange,
                   name              = EXCLUDED.name,
                   is_delisted       = FALSE,
                   last_vendor_check = EXCLUDED.last_vendor_check,
                   updated_at        = EXCLUDED.updated_at
            """;

    private static final String MARK_DELISTED_IF_NOT_SYNCED_AT_SQL =
            """
            UPDATE symbols
               SET is_delisted = TRUE,
                   updated_at  = :asOf
             WHERE is_delisted = FALSE
               AND last_vendor_check IS DISTINCT FROM :asOf
            """;

    private static final String FIND_BY_TICKER_SQL =
            """
            SELECT ticker, exchange, name, is_delisted, last_vendor_check, created_at, updated_at
              FROM symbols
             WHERE ticker = :ticker
            """;

    private static final String DELETE_BY_TICKER_SQL = "DELETE FROM symbols WHERE ticker = :ticker";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSymbolRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Symbol save(Symbol symbol) {
        jdbc.update(SAVE_SQL, toSaveParameters(symbol));
        return symbol;
    }

    @Override
    public Optional<Symbol> findByTicker(Ticker ticker) {
        try {
            Symbol found = jdbc.queryForObject(
                    FIND_BY_TICKER_SQL,
                    new MapSqlParameterSource("ticker", ticker.value()),
                    JdbcSymbolRepository::mapRow);
            return Optional.ofNullable(found);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteByTicker(Ticker ticker) {
        jdbc.update(DELETE_BY_TICKER_SQL, new MapSqlParameterSource("ticker", ticker.value()));
    }

    @Override
    public int upsertAll(Collection<Symbol> symbols, Instant asOf) {
        if (symbols.isEmpty()) {
            return 0;
        }
        List<Symbol> ordered = List.copyOf(symbols);
        int total = 0;
        for (int fromIndex = 0; fromIndex < ordered.size(); fromIndex += BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + BATCH_SIZE, ordered.size());
            total += executeUpsertBatch(ordered.subList(fromIndex, toIndex), asOf);
        }
        return total;
    }

    @Override
    public int markDelistedIfNotSyncedAt(Instant asOf) {
        MapSqlParameterSource params = new MapSqlParameterSource("asOf", toOffsetDateTime(asOf));
        return jdbc.update(MARK_DELISTED_IF_NOT_SYNCED_AT_SQL, params);
    }

    private int executeUpsertBatch(List<Symbol> chunk, Instant asOf) {
        SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
        for (int i = 0; i < chunk.size(); i++) {
            batch[i] = toUpsertParameters(chunk.get(i), asOf);
        }
        int[] rowsPerStatement = jdbc.batchUpdate(UPSERT_SQL, batch);
        int sum = 0;
        for (int rows : rowsPerStatement) {
            sum += rows;
        }
        return sum;
    }

    private static SqlParameterSource toSaveParameters(Symbol symbol) {
        return new MapSqlParameterSource()
                .addValue("ticker", symbol.ticker().value())
                .addValue("exchange", symbol.exchange().dbValue())
                .addValue("name", symbol.name())
                .addValue("isDelisted", symbol.isDelisted())
                .addValue("lastVendorCheck", toOffsetDateTime(symbol.lastVendorCheck()))
                .addValue("createdAt", toOffsetDateTime(symbol.createdAt()))
                .addValue("updatedAt", toOffsetDateTime(symbol.updatedAt()));
    }

    private static SqlParameterSource toUpsertParameters(Symbol symbol, Instant asOf) {
        return new MapSqlParameterSource()
                .addValue("ticker", symbol.ticker().value())
                .addValue("exchange", symbol.exchange().dbValue())
                .addValue("name", symbol.name())
                .addValue("asOf", toOffsetDateTime(asOf));
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static Symbol mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Symbol(
                new Ticker(rs.getString("ticker")),
                Exchange.fromDbValue(rs.getString("exchange")),
                rs.getString("name"),
                rs.getBoolean("is_delisted"),
                toInstant(rs.getObject("last_vendor_check", OffsetDateTime.class)),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)));
    }
}
