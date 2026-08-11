package io.github.rafaeljc.argus.transactions.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLedgerHoldings implements LedgerHoldings {

    private static final String TIMELINE_SQL =
            """
            SELECT ticker, trade_date,
                   SUM(day_net) OVER (PARTITION BY ticker ORDER BY trade_date) AS net
              FROM (SELECT ticker, trade_date,
                           SUM(CASE WHEN operation = 'BUY' THEN quantity ELSE -quantity END) AS day_net
                      FROM transactions
                     WHERE user_id = :userId AND trade_date <= :through
                     GROUP BY ticker, trade_date) d
             ORDER BY trade_date, ticker
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcLedgerHoldings(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<NetQuantityPoint> timeline(UserId userId, LocalDate through) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("through", through);
        return jdbc.query(TIMELINE_SQL, params, (rs, rowNum) -> new NetQuantityPoint(
                new Ticker(rs.getString("ticker")),
                rs.getObject("trade_date", LocalDate.class),
                rs.getBigDecimal("net")));
    }
}
