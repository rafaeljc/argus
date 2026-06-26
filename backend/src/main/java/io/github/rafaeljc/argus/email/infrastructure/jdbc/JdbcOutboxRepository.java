package io.github.rafaeljc.argus.email.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    // Caps retry attempts before the poller gives up on a row. Lives here, not inlined in the SQL,
    // so the claim query and any future ops query (e.g. "stuck rows") share one source of truth.
    static final int MAX_ERROR_COUNT = 10;

    // ON CONFLICT DO NOTHING keeps the caller's transaction usable on duplicate idempotence keys
    // (a unique-violation exception would mark the surrounding transaction as aborted in Postgres).
    private static final String INSERT_SQL =
            """
            INSERT INTO outbox (id, aggregate_id, event_type, payload, idempotence_key, created_at)
            VALUES (:id, :aggregate_id, :event_type, CAST(:payload AS jsonb), :idempotence_key, :created_at)
            ON CONFLICT (idempotence_key) DO NOTHING
            """;

    private static final String CLAIM_SQL =
            """
            UPDATE outbox
               SET published_by_worker_id = :worker_id
             WHERE id IN (
                SELECT id
                  FROM outbox
                 WHERE published_at IS NULL
                   AND error_count < %d
                   AND created_at <= :now
                 ORDER BY created_at
                 LIMIT :limit
                 FOR UPDATE SKIP LOCKED
             )
            RETURNING id, aggregate_id, event_type, payload::text AS payload,
                      idempotence_key, created_at, published_at, error_count,
                      last_error, published_by_worker_id
            """.formatted(MAX_ERROR_COUNT);

    private static final String MARK_PUBLISHED_SQL =
            """
            UPDATE outbox
               SET published_at = :published_at
             WHERE id = :id
            """;

    private static final String RECORD_FAILURE_SQL =
            """
            UPDATE outbox
               SET error_count = error_count + 1,
                   last_error  = :last_error
             WHERE id = :id
            """;

    private static final RowMapper<OutboxMessage> ROW_MAPPER = JdbcOutboxRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(OutboxMessage message) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", message.id().value())
                .addValue("aggregate_id", message.aggregateId())
                .addValue("event_type", message.eventType().dbValue())
                .addValue("payload", message.payload())
                .addValue("idempotence_key", message.idempotenceKey())
                .addValue("created_at", toOffsetDateTime(message.createdAt()));
        return jdbc.update(INSERT_SQL, params) > 0;
    }

    @Override
    public List<OutboxMessage> claimUnpublishedBatch(int limit, String workerId, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("worker_id", workerId)
                .addValue("limit", limit)
                .addValue("now", toOffsetDateTime(now));
        return jdbc.query(CLAIM_SQL, params, ROW_MAPPER);
    }

    @Override
    public void markPublished(OutboxId id, Instant publishedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("published_at", toOffsetDateTime(publishedAt));
        jdbc.update(MARK_PUBLISHED_SQL, params);
    }

    @Override
    public void recordFailure(OutboxId id, String errorMessage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("last_error", errorMessage);
        jdbc.update(RECORD_FAILURE_SQL, params);
    }

    private static OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime publishedAt = rs.getObject("published_at", OffsetDateTime.class);
        return new OutboxMessage(
                new OutboxId(rs.getObject("id", UUID.class)),
                rs.getObject("aggregate_id", UUID.class),
                EventType.fromDbValue(rs.getString("event_type")),
                rs.getString("payload"),
                rs.getString("idempotence_key"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                publishedAt == null ? null : publishedAt.toInstant(),
                rs.getInt("error_count"),
                rs.getString("last_error"),
                rs.getString("published_by_worker_id"));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
