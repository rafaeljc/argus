package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillStatus;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBackfillStatus implements BackfillStatus {

    // backfill_jobs_ticker_active_uidx guarantees at most one active row per ticker,
    // so no DISTINCT is needed on the batch query.
    private static final List<String> ACTIVE_STATUSES = List.of(
            JobStatus.PENDING.dbValue(),
            JobStatus.IN_PROGRESS.dbValue());

    private static final String SELECT_ACTIVE_EXISTS_FOR_TICKER =
            """
            SELECT EXISTS (
                SELECT 1
                  FROM backfill_jobs
                 WHERE ticker = :ticker
                   AND status IN (:statuses)
            )
            """;

    private static final String SELECT_ACTIVE_TICKERS_IN =
            """
            SELECT ticker
              FROM backfill_jobs
             WHERE ticker IN (:tickers)
               AND status IN (:statuses)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcBackfillStatus(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isPending(Ticker ticker) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ticker", ticker.value())
                .addValue("statuses", ACTIVE_STATUSES);
        Boolean exists = jdbc.queryForObject(SELECT_ACTIVE_EXISTS_FOR_TICKER, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Set<Ticker> pendingAmong(Set<Ticker> tickers) {
        if (tickers.isEmpty()) {
            return Set.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tickers", tickers.stream().map(Ticker::value).toList())
                .addValue("statuses", ACTIVE_STATUSES);
        List<String> active = jdbc.queryForList(SELECT_ACTIVE_TICKERS_IN, params, String.class);
        return active.stream().map(Ticker::new).collect(Collectors.toCollection(HashSet::new));
    }
}
