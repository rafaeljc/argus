package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSymbolLookup implements SymbolLookup {

    private static final String SELECT_IS_DELISTED_BY_TICKER =
            "SELECT is_delisted FROM symbols WHERE ticker = :ticker";

    private static final String SELECT_DELISTED_TICKERS_IN =
            """
            SELECT ticker
              FROM symbols
             WHERE ticker IN (:tickers)
               AND is_delisted = TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSymbolLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(Ticker ticker) {
        return findIsDelisted(ticker) != null;
    }

    @Override
    public boolean isDelisted(Ticker ticker) {
        Boolean isDelisted = findIsDelisted(ticker);
        return Boolean.TRUE.equals(isDelisted);
    }

    @Override
    public Set<Ticker> delistedAmong(Set<Ticker> tickers) {
        if (tickers.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("tickers", toTickerValues(tickers));
        List<String> delisted = jdbc.queryForList(SELECT_DELISTED_TICKERS_IN, params, String.class);
        return toTickerSet(delisted);
    }

    // Returns null when the ticker row does not exist — distinguishes "unknown" from "known & active".
    private Boolean findIsDelisted(Ticker ticker) {
        MapSqlParameterSource params = new MapSqlParameterSource("ticker", ticker.value());
        try {
            return jdbc.queryForObject(SELECT_IS_DELISTED_BY_TICKER, params, Boolean.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private static List<String> toTickerValues(Set<Ticker> tickers) {
        return tickers.stream().map(Ticker::value).toList();
    }

    private static Set<Ticker> toTickerSet(List<String> values) {
        return values.stream().map(Ticker::new).collect(Collectors.toCollection(HashSet::new));
    }
}
