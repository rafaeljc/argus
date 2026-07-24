package io.github.rafaeljc.argus.alerts.infrastructure.jdbc;

import io.github.rafaeljc.argus.alerts.application.port.AlertRuleRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.alerts.domain.DuplicateAlertRuleException;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAlertRuleRepository implements AlertRuleRepository {

    private static final String SIGNATURE_UNIQUE_INDEX = "alert_rules_user_signature_uidx";

    private static final String INSERT_SQL =
            """
            INSERT INTO alert_rules (id, user_id, direction, threshold, window_days, created_at)
            VALUES (:id, :userId, :direction, :threshold, :windowDays, :createdAt)
            """;

    private static final String FIND_ACTIVE_BY_ID_AND_USER_SQL =
            """
            SELECT id, user_id, direction, threshold, window_days, created_at
            FROM alert_rules
            WHERE id = :id AND user_id = :userId
            """;

    private static final String COUNT_ACTIVE_BY_USER_SQL =
            "SELECT count(*) FROM alert_rules WHERE user_id = :userId";

    private static final String LIST_ACTIVE_BY_USER_ORDERED_BY_CREATED_AT_DESC_SQL =
            """
            SELECT id, user_id, direction, threshold, window_days, created_at
            FROM alert_rules
            WHERE user_id = :userId
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String DELETE_ACTIVE_AND_RETURN_SQL =
            """
            DELETE FROM alert_rules
            WHERE id = :id AND user_id = :userId
            RETURNING id, user_id, direction, threshold, window_days, created_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAlertRuleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AlertRule insert(AlertRule rule) {
        try {
            jdbc.update(INSERT_SQL, paramsFor(rule));
        } catch (DataIntegrityViolationException e) {
            if (isSignatureUniqueViolation(e)) {
                throw new DuplicateAlertRuleException(rule.direction(), rule.threshold(), rule.window());
            }
            throw e;
        }
        return rule;
    }

    @Override
    public int countActiveByUser(UserId userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId.value());
        Integer count = jdbc.queryForObject(COUNT_ACTIVE_BY_USER_SQL, params, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<AlertRule> findActiveByIdAndUser(RuleId id, UserId userId) {
        List<AlertRule> rows =
                jdbc.query(FIND_ACTIVE_BY_ID_AND_USER_SQL, idParams(id, userId), JdbcAlertRuleRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<AlertRule> listActiveByUserOrderedByCreatedAtDesc(UserId userId, int page, int perPage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.value())
                .addValue("limit", perPage)
                .addValue("offset", (page - 1) * perPage);
        return jdbc.query(LIST_ACTIVE_BY_USER_ORDERED_BY_CREATED_AT_DESC_SQL, params, JdbcAlertRuleRepository::mapRow);
    }

    @Override
    public Optional<AlertRule> deleteActiveAndReturn(RuleId id, UserId userId) {
        List<AlertRule> rows =
                jdbc.query(DELETE_ACTIVE_AND_RETURN_SQL, idParams(id, userId), JdbcAlertRuleRepository::mapRow);
        return rows.stream().findFirst();
    }

    private static boolean isSignatureUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? ex.getMessage() : cause.getMessage();
        return message != null && message.contains(SIGNATURE_UNIQUE_INDEX);
    }

    private static MapSqlParameterSource idParams(RuleId id, UserId userId) {
        return new MapSqlParameterSource().addValue("id", id.value()).addValue("userId", userId.value());
    }

    private static MapSqlParameterSource paramsFor(AlertRule rule) {
        return new MapSqlParameterSource()
                .addValue("id", rule.id().value())
                .addValue("userId", rule.userId().value())
                .addValue("direction", rule.direction().name())
                .addValue("threshold", rule.threshold().value())
                .addValue("windowDays", rule.window().days())
                .addValue("createdAt", toOffsetDateTime(rule.createdAt()));
    }

    private static AlertRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AlertRule(
                new RuleId(rs.getObject("id", UUID.class)),
                new UserId(rs.getObject("user_id", UUID.class)),
                Direction.valueOf(rs.getString("direction")),
                new Percentage(rs.getBigDecimal("threshold")),
                new AlertLookbackWindow(rs.getInt("window_days")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
