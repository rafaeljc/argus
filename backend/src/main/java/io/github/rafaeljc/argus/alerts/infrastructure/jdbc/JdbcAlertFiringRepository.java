package io.github.rafaeljc.argus.alerts.infrastructure.jdbc;

import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAlertFiringRepository implements AlertFiringRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO alert_firings (
                id, user_id, rule_id, direction, threshold, window_days, fired_at,
                portfolio_value_start, portfolio_value_end, percent_change,
                window_start_date, window_end_date)
            VALUES (
                :id, :userId, :ruleId, :direction, :threshold, :windowDays, :firedAt,
                :portfolioValueStart, :portfolioValueEnd, :percentChange,
                :windowStartDate, :windowEndDate)
            """;

    private static final String FIND_BY_ID_AND_USER_SQL =
            """
            SELECT id, user_id, rule_id, direction, threshold, window_days, fired_at,
                   portfolio_value_start, portfolio_value_end, percent_change,
                   window_start_date, window_end_date
            FROM alert_firings
            WHERE id = :id AND user_id = :userId
            """;

    private static final String LIST_BY_USER_ORDERED_BY_FIRED_AT_DESC_SQL =
            """
            SELECT id, user_id, rule_id, direction, threshold, window_days, fired_at,
                   portfolio_value_start, portfolio_value_end, percent_change,
                   window_start_date, window_end_date
            FROM alert_firings
            WHERE user_id = :userId
            ORDER BY fired_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAlertFiringRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AlertFiring insert(AlertFiring firing) {
        jdbc.update(INSERT_SQL, paramsFor(firing));
        return firing;
    }

    @Override
    public Optional<AlertFiring> findByIdAndUser(FiringId id, UserId userId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("id", id.value()).addValue("userId", userId.value());
        List<AlertFiring> rows = jdbc.query(FIND_BY_ID_AND_USER_SQL, params, JdbcAlertFiringRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<AlertFiring> listByUserOrderedByFiredAtDesc(UserId userId, int page, int perPage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("limit", perPage)
                .addValue("offset", (page - 1) * perPage);
        return jdbc.query(LIST_BY_USER_ORDERED_BY_FIRED_AT_DESC_SQL, params, JdbcAlertFiringRepository::mapRow);
    }

    private static MapSqlParameterSource paramsFor(AlertFiring firing) {
        return new MapSqlParameterSource()
                .addValue("id", firing.id().value())
                .addValue("userId", firing.userId().value())
                .addValue("ruleId", firing.ruleId().value())
                .addValue("direction", firing.direction().name())
                .addValue("threshold", firing.threshold().value())
                .addValue("windowDays", firing.window().days())
                .addValue("firedAt", toOffsetDateTime(firing.firedAt()))
                .addValue("portfolioValueStart", firing.portfolioValueStart().value())
                .addValue("portfolioValueEnd", firing.portfolioValueEnd().value())
                .addValue("percentChange", firing.percentChange())
                .addValue("windowStartDate", firing.windowStartDate())
                .addValue("windowEndDate", firing.windowEndDate());
    }

    private static AlertFiring mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AlertFiring(
                new FiringId(rs.getObject("id", UUID.class)),
                new UserId(rs.getObject("user_id", UUID.class)),
                new RuleId(rs.getObject("rule_id", UUID.class)),
                Direction.valueOf(rs.getString("direction")),
                new Percentage(rs.getBigDecimal("threshold")),
                new AlertLookbackWindow(rs.getInt("window_days")),
                rs.getObject("fired_at", OffsetDateTime.class).toInstant(),
                new Money(rs.getBigDecimal("portfolio_value_start")),
                new Money(rs.getBigDecimal("portfolio_value_end")),
                rs.getBigDecimal("percent_change"),
                rs.getObject("window_start_date", LocalDate.class),
                rs.getObject("window_end_date", LocalDate.class));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
