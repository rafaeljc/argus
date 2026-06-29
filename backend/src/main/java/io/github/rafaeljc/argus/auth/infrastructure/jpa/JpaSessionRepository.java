package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSessionRepository implements SessionRepository {

    private static final String TOUCH_SQL =
            """
            UPDATE sessions
               SET last_activity_at = :lastActivityAt,
                   expires_at       = :expiresAt,
                   ip_address       = :ipAddress,
                   user_agent       = :userAgent
             WHERE id = :id
            """;

    private final SpringDataSessionJpaRepository jpa;
    private final NamedParameterJdbcTemplate jdbc;

    JpaSessionRepository(SpringDataSessionJpaRepository jpa, NamedParameterJdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public Session save(Session session) {
        SessionJpaEntity persisted = jpa.save(SessionEntityMapper.toEntity(session));
        return SessionEntityMapper.toDomain(persisted);
    }

    @Override
    public Optional<Session> findByTokenHash(String sessionTokenHash) {
        return jpa.findBySessionTokenHash(sessionTokenHash).map(SessionEntityMapper::toDomain);
    }

    @Override
    public List<Session> findByUserId(UserId userId) {
        return jpa.findByUserId(userId.value()).stream().map(SessionEntityMapper::toDomain).toList();
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
        jpa.deleteById(id.value());
    }

    // pgjdbc cannot infer a SQL type from java.time.Instant; OffsetDateTime maps to
    // timestamptz natively via JDBC 4.2.
    private static OffsetDateTime asTimestampTz(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
