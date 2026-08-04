package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcHeldTickers implements HeldTickers {

    private static final String SELECT_SQL =
            "SELECT DISTINCT ticker FROM holdings WHERE user_id IN (:userIds)";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcHeldTickers(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Ticker> findForUserIds(Collection<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = userIds.stream().map(UserId::value).toList();
        MapSqlParameterSource params = new MapSqlParameterSource("userIds", ids);
        List<String> tickers = jdbc.queryForList(SELECT_SQL, params, String.class);
        return tickers.stream().map(Ticker::new).collect(Collectors.toSet());
    }
}
