package io.github.rafaeljc.argus.eodpipeline.infrastructure.jdbc;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEodPipelineRunRepository implements EodPipelineRunRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO eod_pipeline_runs
                (id, run_date, trigger, status, started_at, finished_at,
                 step_symbols_status, step_prices_status, step_evaluate_status, error_message)
            VALUES
                (:id, :runDate, :trigger, :status, :startedAt, :finishedAt,
                 :stepSymbolsStatus, :stepPricesStatus, :stepEvaluateStatus, :errorMessage)
            """;

    private static final String UPDATE_SQL =
            """
            UPDATE eod_pipeline_runs
               SET status               = :status,
                   finished_at          = :finishedAt,
                   step_symbols_status  = :stepSymbolsStatus,
                   step_prices_status   = :stepPricesStatus,
                   step_evaluate_status = :stepEvaluateStatus,
                   error_message        = :errorMessage
             WHERE id = :id
            """;

    private static final String SELECT_COLUMNS =
            """
            SELECT id, run_date, trigger, status, started_at, finished_at,
                   step_symbols_status, step_prices_status, step_evaluate_status, error_message
            FROM eod_pipeline_runs
            """;

    private static final String FIND_BY_ID_SQL = SELECT_COLUMNS + " WHERE id = :id";

    private static final String FIND_ACTIVE_FOR_DATE_SQL =
            SELECT_COLUMNS + " WHERE run_date = :runDate AND status IN ('pending', 'in_progress')";

    private static final String LIST_PAGED_SQL =
            SELECT_COLUMNS + " ORDER BY started_at DESC, id DESC LIMIT :limit OFFSET :offset";

    private static final String COUNT_SQL = "SELECT count(*) FROM eod_pipeline_runs";

    private static final String IN_PROGRESS_UNIQUE_INDEX = "eod_pipeline_runs_in_progress_uidx";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcEodPipelineRunRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EodPipelineRun insert(EodPipelineRun run) {
        executeTranslatingActiveRunConflict(INSERT_SQL, run);
        return run;
    }

    @Override
    public EodPipelineRun update(EodPipelineRun run) {
        // Postgres re-checks eod_pipeline_runs_in_progress_uidx on UPDATE too: a rerun flips
        // status back to in_progress, which can collide with another active run for the same
        // run_date. Same translation as insert() so that collision surfaces as 409, not 500.
        executeTranslatingActiveRunConflict(UPDATE_SQL, run);
        return run;
    }

    private void executeTranslatingActiveRunConflict(String sql, EodPipelineRun run) {
        try {
            jdbc.update(sql, paramsFor(run));
        } catch (DataIntegrityViolationException e) {
            if (isActiveRunUniqueViolation(e)) {
                throw new RunAlreadyActiveException(run.runDate());
            }
            throw e;
        }
    }

    @Override
    public Optional<EodPipelineRun> findById(RunId id) {
        List<EodPipelineRun> rows = jdbc.query(
                FIND_BY_ID_SQL, new MapSqlParameterSource("id", id.value()), JdbcEodPipelineRunRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<EodPipelineRun> findActiveForDate(LocalDate runDate) {
        List<EodPipelineRun> rows = jdbc.query(
                FIND_ACTIVE_FOR_DATE_SQL,
                new MapSqlParameterSource("runDate", runDate),
                JdbcEodPipelineRunRepository::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<EodPipelineRun> listPaged(int page, int perPage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", perPage)
                .addValue("offset", (page - 1) * perPage);
        return jdbc.query(LIST_PAGED_SQL, params, JdbcEodPipelineRunRepository::mapRow);
    }

    @Override
    public int count() {
        Integer count = jdbc.queryForObject(COUNT_SQL, new MapSqlParameterSource(), Integer.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource paramsFor(EodPipelineRun run) {
        return new MapSqlParameterSource()
                .addValue("id", run.id().value())
                .addValue("runDate", run.runDate())
                .addValue("trigger", run.trigger().dbValue())
                .addValue("status", run.status().dbValue())
                .addValue("startedAt", toOffsetDateTime(run.startedAt()))
                .addValue("finishedAt", toOffsetDateTime(run.finishedAt()))
                .addValue("stepSymbolsStatus", run.stepSymbolsStatus().dbValue())
                .addValue("stepPricesStatus", run.stepPricesStatus().dbValue())
                .addValue("stepEvaluateStatus", run.stepEvaluateStatus().dbValue())
                .addValue("errorMessage", run.errorMessage());
    }

    private static boolean isActiveRunUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        String message = cause == null ? e.getMessage() : cause.getMessage();
        return message != null && message.contains(IN_PROGRESS_UNIQUE_INDEX);
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static EodPipelineRun mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EodPipelineRun(
                new RunId(rs.getObject("id", UUID.class)),
                rs.getObject("run_date", LocalDate.class),
                Trigger.fromDbValue(rs.getString("trigger")),
                RunStatus.fromDbValue(rs.getString("status")),
                toInstant(rs.getObject("started_at", OffsetDateTime.class)),
                toInstant(rs.getObject("finished_at", OffsetDateTime.class)),
                StepStatus.fromDbValue(rs.getString("step_symbols_status")),
                StepStatus.fromDbValue(rs.getString("step_prices_status")),
                StepStatus.fromDbValue(rs.getString("step_evaluate_status")),
                rs.getString("error_message"));
    }
}
