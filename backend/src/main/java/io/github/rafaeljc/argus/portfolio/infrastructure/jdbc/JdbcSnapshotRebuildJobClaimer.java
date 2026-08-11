package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobClaimer;
import io.github.rafaeljc.argus.portfolio.domain.RebuildJobStatus;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSnapshotRebuildJobClaimer implements SnapshotRebuildJobClaimer {

    private static final String CLAIM_SQL =
            """
            UPDATE snapshot_rebuild_jobs
               SET status = 'in_progress',
                   started_at = :now
             WHERE id = (
                SELECT id
                  FROM snapshot_rebuild_jobs
                 WHERE status = 'pending'
                 ORDER BY requested_at
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED
             )
            RETURNING id, user_id, status, requested_at, started_at, completed_at, error_message
            """;

    private static final String MARK_COMPLETED_SQL =
            """
            UPDATE snapshot_rebuild_jobs
               SET status = 'completed',
                   completed_at = :completed_at
             WHERE id = :id
            """;

    private static final String MARK_FAILED_SQL =
            """
            UPDATE snapshot_rebuild_jobs
               SET status = 'failed',
                   error_message = :error_message,
                   completed_at = :completed_at
             WHERE id = :id
            """;

    private static final RowMapper<SnapshotRebuildJob> ROW_MAPPER = JdbcSnapshotRebuildJobClaimer::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSnapshotRebuildJobClaimer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SnapshotRebuildJob> claimNextPending(Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", toOffsetDateTime(now));
        List<SnapshotRebuildJob> claimed = jdbc.query(CLAIM_SQL, params, ROW_MAPPER);
        return claimed.stream().findFirst();
    }

    @Override
    public void markCompleted(JobId id, Instant completedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("completed_at", toOffsetDateTime(completedAt));
        jdbc.update(MARK_COMPLETED_SQL, params);
    }

    @Override
    public void markFailed(JobId id, String errorMessage, Instant completedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("error_message", errorMessage)
                .addValue("completed_at", toOffsetDateTime(completedAt));
        jdbc.update(MARK_FAILED_SQL, params);
    }

    private static SnapshotRebuildJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime startedAt = rs.getObject("started_at", OffsetDateTime.class);
        OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
        return new SnapshotRebuildJob(
                new JobId(rs.getObject("id", UUID.class)),
                new UserId(rs.getObject("user_id", UUID.class)),
                RebuildJobStatus.fromDbValue(rs.getString("status")),
                rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                startedAt == null ? null : startedAt.toInstant(),
                completedAt == null ? null : completedAt.toInstant(),
                rs.getString("error_message"));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
