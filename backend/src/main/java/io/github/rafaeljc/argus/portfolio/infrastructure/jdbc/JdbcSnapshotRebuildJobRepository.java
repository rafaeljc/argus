package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobRepository;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSnapshotRebuildJobRepository implements SnapshotRebuildJobRepository {

    private static final String ENQUEUE_IF_NO_ACTIVE_JOB_SQL =
            """
            INSERT INTO snapshot_rebuild_jobs (id, user_id, status, requested_at)
            VALUES (:id, :userId, :status, :requestedAt)
            ON CONFLICT (user_id) WHERE status IN ('pending', 'in_progress') DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSnapshotRebuildJobRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean enqueueIfNoActiveJob(SnapshotRebuildJob job) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", job.id().value())
                .addValue("userId", job.userId().value())
                .addValue("status", job.status().dbValue())
                .addValue("requestedAt", toOffsetDateTime(job.requestedAt()));
        return jdbc.update(ENQUEUE_IF_NO_ACTIVE_JOB_SQL, params) > 0;
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
