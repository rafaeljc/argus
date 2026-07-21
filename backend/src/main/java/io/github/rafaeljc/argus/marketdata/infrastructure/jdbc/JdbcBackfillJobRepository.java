package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBackfillJobRepository implements BackfillJobRepository {

    private static final String UPSERT_SQL =
            """
            INSERT INTO backfill_jobs
                (id, ticker, user_id, status, start_date, end_date, price_count, error_message,
                 created_at, started_at, completed_at)
            VALUES
                (:id, :ticker, :userId, :status, :startDate, :endDate, :priceCount, :errorMessage,
                 :createdAt, :startedAt, :completedAt)
            ON CONFLICT (id) DO UPDATE
               SET status        = EXCLUDED.status,
                   price_count   = EXCLUDED.price_count,
                   error_message = EXCLUDED.error_message,
                   started_at    = EXCLUDED.started_at,
                   completed_at  = EXCLUDED.completed_at
            """;

    private static final String FIND_BY_ID_SQL =
            """
            SELECT id, ticker, user_id, status, start_date, end_date, price_count, error_message,
                   created_at, started_at, completed_at
            FROM backfill_jobs
            WHERE id = :id
            """;

    private static final String FIND_ACTIVE_BY_TICKER_SQL =
            """
            SELECT id, ticker, user_id, status, start_date, end_date, price_count, error_message,
                   created_at, started_at, completed_at
            FROM backfill_jobs
            WHERE ticker = :ticker AND status IN ('pending', 'in_progress')
            """;

    private static final String ENQUEUE_IF_NO_ACTIVE_JOB_SQL =
            """
            INSERT INTO backfill_jobs (id, ticker, user_id, status, start_date, end_date, created_at)
            VALUES (:id, :ticker, :userId, :status, :startDate, :endDate, :createdAt)
            ON CONFLICT (ticker) WHERE status IN ('pending', 'in_progress') DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcBackfillJobRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BackfillJob save(BackfillJob job) {
        jdbc.update(UPSERT_SQL, paramsFor(job));
        return job;
    }

    @Override
    public Optional<BackfillJob> findById(JobId id) {
        List<BackfillJob> rows = jdbc.query(
                FIND_BY_ID_SQL, new MapSqlParameterSource("id", id.value()), JdbcBackfillJobRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<BackfillJob> findActiveByTicker(Ticker ticker) {
        List<BackfillJob> rows = jdbc.query(
                FIND_ACTIVE_BY_TICKER_SQL,
                new MapSqlParameterSource("ticker", ticker.value()),
                JdbcBackfillJobRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public boolean enqueueIfNoActiveJob(BackfillJob job) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", job.id().value())
                .addValue("ticker", job.ticker().value())
                .addValue("userId", job.userId().value())
                .addValue("status", job.status().dbValue())
                .addValue("startDate", job.startDate())
                .addValue("endDate", job.endDate())
                .addValue("createdAt", toOffsetDateTime(job.createdAt()));
        return jdbc.update(ENQUEUE_IF_NO_ACTIVE_JOB_SQL, params) > 0;
    }

    private static MapSqlParameterSource paramsFor(BackfillJob job) {
        return new MapSqlParameterSource()
                .addValue("id", job.id().value())
                .addValue("ticker", job.ticker().value())
                .addValue("userId", job.userId().value())
                .addValue("status", job.status().dbValue())
                .addValue("startDate", job.startDate())
                .addValue("endDate", job.endDate())
                .addValue("priceCount", job.priceCount())
                .addValue("errorMessage", job.errorMessage())
                .addValue("createdAt", toOffsetDateTime(job.createdAt()))
                .addValue("startedAt", toOffsetDateTime(job.startedAt()))
                .addValue("completedAt", toOffsetDateTime(job.completedAt()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static BackfillJob mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BackfillJob(
                new JobId(rs.getObject("id", UUID.class)),
                new Ticker(rs.getString("ticker")),
                new UserId(rs.getObject("user_id", UUID.class)),
                JobStatus.fromDbValue(rs.getString("status")),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                (Integer) rs.getObject("price_count"),
                rs.getString("error_message"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("started_at", OffsetDateTime.class)),
                toInstant(rs.getObject("completed_at", OffsetDateTime.class)));
    }
}
