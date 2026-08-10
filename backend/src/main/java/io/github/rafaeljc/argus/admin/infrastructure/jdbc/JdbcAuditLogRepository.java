package io.github.rafaeljc.argus.admin.infrastructure.jdbc;

import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO admin_audit_log (id, actor_id, action, target_user_id, metadata, created_at)
            VALUES (:id, :actor_id, :action, :target_user_id, CAST(:metadata AS jsonb), :created_at)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(AuditLogEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id().value())
                .addValue("actor_id", entry.actorId().value())
                .addValue("action", entry.action().dbValue())
                .addValue("target_user_id", entry.targetUserId() == null ? null : entry.targetUserId().value())
                .addValue("metadata", toJson(entry.metadata()))
                .addValue("created_at", OffsetDateTime.ofInstant(entry.createdAt(), ZoneOffset.UTC));
        jdbc.update(INSERT_SQL, params);
    }

    private String toJson(AuditMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Map<String, Object> fields = switch (metadata) {
            case AuditMetadata.UserAction userAction ->
                    Collections.singletonMap("reason", userAction.reason());
            case AuditMetadata.EodRun eodRun ->
                    Map.of("run_id", eodRun.runId().value().toString(), "run_date", eodRun.runDate().toString());
            case AuditMetadata.EodStepRerun eodStepRerun ->
                    Map.of("run_id", eodStepRerun.runId().value().toString(), "step", eodStepRerun.step());
        };
        return objectMapper.writeValueAsString(fields);
    }
}
