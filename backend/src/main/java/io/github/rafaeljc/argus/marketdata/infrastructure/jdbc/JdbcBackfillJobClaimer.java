package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobClaimer;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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
class JdbcBackfillJobClaimer implements BackfillJobClaimer {

    private static final String CLAIM_SQL =
            """
            UPDATE backfill_jobs
               SET status = 'in_progress',
                   started_at = :now
             WHERE id = (
                SELECT id
                  FROM backfill_jobs
                 WHERE status = 'pending'
                 ORDER BY created_at
                 LIMIT 1
                 FOR UPDATE SKIP LOCKED
             )
            RETURNING id, ticker, user_id, status, start_date, end_date, price_count,
                      error_message, created_at, started_at, completed_at
            """;

    private static final String MARK_COMPLETED_SQL =
            """
            UPDATE backfill_jobs
               SET status = 'completed',
                   price_count = :price_count,
                   completed_at = :completed_at
             WHERE id = :id
            """;

    private static final String MARK_FAILED_SQL =
            """
            UPDATE backfill_jobs
               SET status = 'failed',
                   error_message = :error_message,
                   completed_at = :completed_at
             WHERE id = :id
            """;

    private static final String REVERT_TO_PENDING_SQL =
            """
            UPDATE backfill_jobs
               SET status = 'pending',
                   started_at = NULL
             WHERE id = :id
            """;

    private static final RowMapper<BackfillJob> ROW_MAPPER = JdbcBackfillJobClaimer::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcBackfillJobClaimer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BackfillJob> claimNextPending(Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", toOffsetDateTime(now));
        List<BackfillJob> claimed = jdbc.query(CLAIM_SQL, params, ROW_MAPPER);
        return claimed.stream().findFirst();
    }

    @Override
    public void markCompleted(JobId id, int priceCount, Instant completedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("price_count", priceCount)
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

    @Override
    public void revertToPending(JobId id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.value());
        jdbc.update(REVERT_TO_PENDING_SQL, params);
    }

    private static BackfillJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime startedAt = rs.getObject("started_at", OffsetDateTime.class);
        OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
        return new BackfillJob(
                new JobId(rs.getObject("id", UUID.class)),
                new Ticker(rs.getString("ticker")),
                new UserId(rs.getObject("user_id", UUID.class)),
                JobStatus.fromDbValue(rs.getString("status")),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                (Integer) rs.getObject("price_count"),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                startedAt == null ? null : startedAt.toInstant(),
                completedAt == null ? null : completedAt.toInstant());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
