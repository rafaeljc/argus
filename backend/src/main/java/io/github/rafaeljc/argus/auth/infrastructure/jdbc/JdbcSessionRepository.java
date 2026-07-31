package io.github.rafaeljc.argus.auth.infrastructure.jdbc;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSessionRepository implements SessionRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO sessions
                (id, user_id, session_token_hash, ip_address, user_agent, created_at, expires_at, last_activity_at)
            VALUES
                (:id, :userId, :sessionTokenHash, :ipAddress, :userAgent, :createdAt, :expiresAt, :lastActivityAt)
            """;

    private static final String SELECT_COLUMNS =
            """
            SELECT id, user_id, session_token_hash, ip_address, user_agent, created_at, expires_at, last_activity_at
            FROM sessions
            """;

    private static final String FIND_BY_ID_SQL = SELECT_COLUMNS + " WHERE id = :id";

    private static final String FIND_BY_TOKEN_HASH_SQL = SELECT_COLUMNS + " WHERE session_token_hash = :tokenHash";

    private static final String FIND_BY_USER_ID_SQL = SELECT_COLUMNS + " WHERE user_id = :userId";

    private static final String TOUCH_SQL =
            """
            UPDATE sessions
               SET last_activity_at = :lastActivityAt,
                   expires_at       = :expiresAt,
                   ip_address       = :ipAddress,
                   user_agent       = :userAgent
             WHERE id = :id
            """;

    private static final String DELETE_BY_ID_SQL =
            """
            DELETE FROM sessions
             WHERE id = :id
            """;

    private static final String DELETE_ALL_FOR_USER_SQL =
            """
            DELETE FROM sessions
             WHERE user_id = :userId
            """;

    // Postgres does not support LIMIT on DELETE directly; the subquery form is portable and lets
    // us cap each batch so a large sweep doesn't hold one long lock over the whole table.
    private static final String DELETE_EXPIRED_BEFORE_SQL =
            """
            DELETE FROM sessions
             WHERE id IN (
                 SELECT id FROM sessions
                  WHERE expires_at < :before
                  LIMIT :batchSize
             )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Session save(Session session) {
        jdbc.update(INSERT_SQL, paramsFor(session));
        return session;
    }

    @Override
    public Optional<Session> findById(SessionId id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id.value());
        return jdbc.query(FIND_BY_ID_SQL, params, JdbcSessionRepository::mapRow).stream().findFirst();
    }

    @Override
    public Optional<Session> findByTokenHash(String sessionTokenHash) {
        MapSqlParameterSource params = new MapSqlParameterSource("tokenHash", sessionTokenHash);
        return jdbc.query(FIND_BY_TOKEN_HASH_SQL, params, JdbcSessionRepository::mapRow).stream().findFirst();
    }

    @Override
    public List<Session> findByUserId(UserId userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId.value());
        return jdbc.query(FIND_BY_USER_ID_SQL, params, JdbcSessionRepository::mapRow);
    }

    @Override
    public void touch(SessionId id, Instant lastActivityAt, Instant expiresAt, String ipAddress, String userAgent) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("lastActivityAt", asTimestampTz(lastActivityAt))
                .addValue("expiresAt", asTimestampTz(expiresAt))
                .addValue("ipAddress", ipAddress)
                .addValue("userAgent", userAgent);
        jdbc.update(TOUCH_SQL, params);
    }

    @Override
    public void deleteById(SessionId id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id.value());
        jdbc.update(DELETE_BY_ID_SQL, params);
    }

    @Override
    public void deleteAllForUser(UserId userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId.value());
        jdbc.update(DELETE_ALL_FOR_USER_SQL, params);
    }

    @Override
    public int deleteExpiredBefore(Instant before, int batchSize) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("before", asTimestampTz(before))
                .addValue("batchSize", batchSize);
        return jdbc.update(DELETE_EXPIRED_BEFORE_SQL, params);
    }

    private static MapSqlParameterSource paramsFor(Session session) {
        return new MapSqlParameterSource()
                .addValue("id", session.id().value())
                .addValue("userId", session.userId().value())
                .addValue("sessionTokenHash", session.sessionTokenHash())
                .addValue("ipAddress", session.ipAddress())
                .addValue("userAgent", session.userAgent())
                .addValue("createdAt", asTimestampTz(session.createdAt()))
                .addValue("expiresAt", asTimestampTz(session.expiresAt()))
                .addValue("lastActivityAt", asTimestampTz(session.lastActivityAt()));
    }

    private static Session mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Session(
                new SessionId(rs.getObject("id", UUID.class)),
                new UserId(rs.getObject("user_id", UUID.class)),
                rs.getString("session_token_hash"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("last_activity_at", OffsetDateTime.class).toInstant());
    }

    // pgjdbc cannot infer a SQL type from java.time.Instant; OffsetDateTime maps to
    // timestamptz natively via JDBC 4.2.
    private static OffsetDateTime asTimestampTz(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
