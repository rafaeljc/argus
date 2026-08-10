package io.github.rafaeljc.argus.admin.infrastructure.jdbc;

import io.github.rafaeljc.argus.admin.application.AuditLogEntryView;
import io.github.rafaeljc.argus.admin.application.AuditLogFilter;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO admin_audit_log (id, actor_id, action, target_user_id, metadata, created_at)
            VALUES (:id, :actor_id, :action, :target_user_id, CAST(:metadata AS jsonb), :created_at)
            """;

    private static final String SELECT_FILTERED_PREFIX =
            "SELECT id, actor_id, action, target_user_id, metadata, created_at FROM admin_audit_log WHERE ";

    private static final String SELECT_FILTERED_SUFFIX =
            " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";

    private static final String COUNT_FILTERED_PREFIX = "SELECT count(*) FROM admin_audit_log WHERE ";

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

    @Override
    public PageResult<AuditLogEntryView> findFiltered(AuditLogFilter filter, int page, int perPage) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = whereClause(filter, params);

        params.addValue("limit", perPage).addValue("offset", (page - 1) * perPage);
        List<AuditLogEntryView> items =
                jdbc.query(SELECT_FILTERED_PREFIX + where + SELECT_FILTERED_SUFFIX, params, this::mapRow);

        Integer total = jdbc.queryForObject(COUNT_FILTERED_PREFIX + where, params, Integer.class);
        return new PageResult<>(items, total == null ? 0 : total, page, perPage);
    }

    private static String whereClause(AuditLogFilter filter, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (filter.actorId() != null) {
            conditions.add("actor_id = :actorId");
            params.addValue("actorId", filter.actorId().value());
        }
        if (filter.targetUserId() != null) {
            conditions.add("target_user_id = :targetUserId");
            params.addValue("targetUserId", filter.targetUserId().value());
        }
        if (filter.action() != null) {
            conditions.add("action = :action");
            params.addValue("action", filter.action().dbValue());
        }
        if (filter.from() != null) {
            conditions.add("created_at >= :from");
            params.addValue("from", OffsetDateTime.ofInstant(filter.from(), ZoneOffset.UTC));
        }
        if (filter.to() != null) {
            conditions.add("created_at <= :to");
            params.addValue("to", OffsetDateTime.ofInstant(filter.to(), ZoneOffset.UTC));
        }
        return conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);
    }

    private AuditLogEntryView mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID targetUserId = rs.getObject("target_user_id", UUID.class);
        return new AuditLogEntryView(
                new AuditEntryId(rs.getObject("id", UUID.class)),
                new UserId(rs.getObject("actor_id", UUID.class)),
                AdminAction.fromDbValue(rs.getString("action")),
                targetUserId == null ? null : new UserId(targetUserId),
                toMetadataMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private Map<String, Object> toMetadataMap(String metadataJson) {
        if (metadataJson == null) {
            return null;
        }
        return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
        });
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
