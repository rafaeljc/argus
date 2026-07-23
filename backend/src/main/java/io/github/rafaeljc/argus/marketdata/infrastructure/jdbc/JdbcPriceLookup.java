package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPriceLookup implements PriceLookup {

    // ORDER BY … DESC LIMIT 1 rides the price_history_ticker_date_desc_idx index — see V1 migration.
    private static final String SELECT_LATEST_CLOSE =
            """
            SELECT ticker, trade_date, close_price
              FROM price_history
             WHERE ticker = :ticker
             ORDER BY trade_date DESC
             LIMIT 1
            """;

    private static final String SELECT_CLOSE_ON_DATE =
            """
            SELECT close_price
              FROM price_history
             WHERE ticker = :ticker
               AND trade_date = :tradeDate
            """;

    private static final String SELECT_CLOSES_ON_DATE_IN =
            """
            SELECT ticker, close_price
              FROM price_history
             WHERE ticker IN (:tickers)
               AND trade_date = :tradeDate
            """;

    // Rides price_history_ticker_date_desc_idx: DISTINCT ON (ticker) keeps one row per ticker —
    // the newest, given the ORDER BY — in a single round trip regardless of ticker count.
    private static final String SELECT_LATEST_CLOSES_IN =
            """
            SELECT DISTINCT ON (ticker) ticker, trade_date, close_price
              FROM price_history
             WHERE ticker IN (:tickers)
             ORDER BY ticker, trade_date DESC
            """;

    private static final RowMapper<Close> CLOSE_ROW_MAPPER = (rs, rowNum) -> new Close(
            new Ticker(rs.getString("ticker")),
            rs.getObject("trade_date", LocalDate.class),
            rs.getBigDecimal("close_price"));

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPriceLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Close> latestClose(Ticker ticker) {
        MapSqlParameterSource params = new MapSqlParameterSource("ticker", ticker.value());
        try {
            return Optional.ofNullable(jdbc.queryForObject(SELECT_LATEST_CLOSE, params, CLOSE_ROW_MAPPER));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<BigDecimal> closeOn(Ticker ticker, LocalDate date) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ticker", ticker.value())
                .addValue("tradeDate", date);
        try {
            return Optional.ofNullable(jdbc.queryForObject(SELECT_CLOSE_ON_DATE, params, BigDecimal.class));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Map<Ticker, BigDecimal> closesOn(Set<Ticker> tickers, LocalDate date) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tickers", tickers.stream().map(Ticker::value).toList())
                .addValue("tradeDate", date);
        Map<Ticker, BigDecimal> closes = new HashMap<>(tickers.size());
        RowCallbackHandler collectRow = rs ->
                closes.put(new Ticker(rs.getString("ticker")), rs.getBigDecimal("close_price"));
        jdbc.query(SELECT_CLOSES_ON_DATE_IN, params, collectRow);
        return closes;
    }

    @Override
    public Map<Ticker, Close> latestCloses(Set<Ticker> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tickers", tickers.stream().map(Ticker::value).toList());
        Map<Ticker, Close> closes = new HashMap<>(tickers.size());
        RowCallbackHandler collectRow = rs -> {
            Close close = CLOSE_ROW_MAPPER.mapRow(rs, 0);
            closes.put(close.ticker(), close);
        };
        jdbc.query(SELECT_LATEST_CLOSES_IN, params, collectRow);
        return closes;
    }
}
