package io.github.rafaeljc.argus.admin.infrastructure.jdbc;

import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO admin_audit_log (id, actor_id, action, target_user_id, metadata, created_at)
            VALUES (:id, :actor_id, :action, :target_user_id, CAST(:metadata AS jsonb), :created_at)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuditLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AuditLogEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id().value())
                .addValue("actor_id", entry.actorId().value())
                .addValue("action", entry.action().dbValue())
                .addValue("target_user_id", entry.targetUserId() == null ? null : entry.targetUserId().value())
                .addValue("metadata", entry.metadataJson())
                .addValue("created_at", OffsetDateTime.ofInstant(entry.createdAt(), ZoneOffset.UTC));
        jdbc.update(INSERT_SQL, params);
    }
}
