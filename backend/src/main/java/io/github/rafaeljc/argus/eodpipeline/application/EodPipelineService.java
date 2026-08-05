package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.marketdata.application.SyncDailyCloses;
import io.github.rafaeljc.argus.marketdata.application.SyncSymbolUniverse;
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EodPipelineService {

    private static final Logger log = LoggerFactory.getLogger(EodPipelineService.class);

    private final EodPipelineRunRepository runs;
    private final SyncSymbolUniverse syncSymbolUniverse;
    private final ActiveUserIds activeUserIds;
    private final HeldTickers heldTickers;
    private final SyncDailyCloses syncDailyCloses;
    private final WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts;
    private final Clock clock;

    public EodPipelineService(
            EodPipelineRunRepository runs,
            SyncSymbolUniverse syncSymbolUniverse,
            ActiveUserIds activeUserIds,
            HeldTickers heldTickers,
            SyncDailyCloses syncDailyCloses,
            WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts,
            Clock clock) {
        this.runs = runs;
        this.syncSymbolUniverse = syncSymbolUniverse;
        this.activeUserIds = activeUserIds;
        this.heldTickers = heldTickers;
        this.syncDailyCloses = syncDailyCloses;
        this.writeSnapshotAndEvaluateAlerts = writeSnapshotAndEvaluateAlerts;
        this.clock = clock;
    }

    // Never lets a vendor/breaker failure escape as an exception: the persisted run is the
    // source of truth for step outcome (surfaced later via the admin re-run endpoint), and an
    // escaping exception under @Transactional would roll back the very FAILED-state write meant
    // to record it.
    @Transactional
    public EodPipelineRun runSymbols(RunId id) {
        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        RunStatus startedStatus = run.status() == RunStatus.PENDING ? RunStatus.IN_PROGRESS : run.status();
        EodPipelineRun started = withSymbolsStep(run, startedStatus, StepStatus.IN_PROGRESS, null, null);
        runs.update(started);

        try {
            syncSymbolUniverse.sync();
            EodPipelineRun succeeded =
                    withSymbolsStep(started, RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, null, null);
            runs.update(succeeded);
            return succeeded;
        } catch (RuntimeException e) {
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            EodPipelineRun failed =
                    withSymbolsStep(started, RunStatus.FAILED, StepStatus.FAILED, clock.now(), message);
            runs.update(failed);
            return failed;
        }
    }

    // Same never-throws contract as runSymbols. Independently callable: does not require
    // stepSymbolsStatus to have already succeeded — step ordering belongs to the trigger, and
    // the admin re-run endpoint invokes single steps by design.
    @Transactional
    public EodPipelineRun runPrices(RunId id) {
        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        RunStatus startedStatus = run.status() == RunStatus.PENDING ? RunStatus.IN_PROGRESS : run.status();
        EodPipelineRun started = withPricesStep(run, startedStatus, StepStatus.IN_PROGRESS, null, null);
        runs.update(started);

        try {
            List<UserId> userIds = activeUserIds.find();
            Set<Ticker> tickers = heldTickers.findForUserIds(userIds);
            syncDailyCloses.sync(tickers, run.runDate());
            EodPipelineRun succeeded =
                    withPricesStep(started, RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, null, null);
            runs.update(succeeded);
            return succeeded;
        } catch (RuntimeException e) {
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            EodPipelineRun failed =
                    withPricesStep(started, RunStatus.FAILED, StepStatus.FAILED, clock.now(), message);
            runs.update(failed);
            return failed;
        }
    }

    // Same never-throws contract as runSymbols/runPrices, but each active user's snapshot+evaluate
    // runs in its own transaction (WriteSnapshotAndEvaluateAlerts is REQUIRES_NEW), so one user's
    // failure never rolls back another user's already-committed work; the step is failed/succeeded
    // in aggregate afterward.
    @Transactional
    public EodPipelineRun runEvaluate(RunId id) {
        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        RunStatus startedStatus = run.status() == RunStatus.PENDING ? RunStatus.IN_PROGRESS : run.status();
        EodPipelineRun started = withEvaluateStep(run, startedStatus, StepStatus.IN_PROGRESS, null, null);
        runs.update(started);

        List<UserId> userIds = activeUserIds.find();
        int failures = 0;
        for (UserId userId : userIds) {
            try {
                writeSnapshotAndEvaluateAlerts.forUser(userId, started.runDate());
            } catch (RuntimeException e) {
                failures++;
                log.warn("eod evaluate failed for user {} run {}", userId.value(), id.value(), e);
            }
        }

        if (failures == 0) {
            EodPipelineRun succeeded =
                    withEvaluateStep(started, RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, null, null);
            runs.update(succeeded);
            return succeeded;
        }
        String message = "evaluate failed for %d of %d users".formatted(failures, userIds.size());
        EodPipelineRun failed = withEvaluateStep(started, RunStatus.FAILED, StepStatus.FAILED, clock.now(), message);
        runs.update(failed);
        return failed;
    }

    @Transactional
    public EodPipelineRun markSucceeded(RunId id) {
        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        EodPipelineRun succeeded = new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), RunStatus.SUCCEEDED, run.startedAt(), clock.now(),
                run.stepSymbolsStatus(), run.stepPricesStatus(), run.stepEvaluateStatus(), run.errorMessage());
        runs.update(succeeded);
        return succeeded;
    }

    private static EodPipelineRun withSymbolsStep(
            EodPipelineRun run,
            RunStatus status,
            StepStatus stepSymbolsStatus,
            Instant finishedAt,
            String errorMessage) {
        return new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), status, run.startedAt(), finishedAt,
                stepSymbolsStatus, run.stepPricesStatus(), run.stepEvaluateStatus(), errorMessage);
    }

    private static EodPipelineRun withPricesStep(
            EodPipelineRun run,
            RunStatus status,
            StepStatus stepPricesStatus,
            Instant finishedAt,
            String errorMessage) {
        return new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), status, run.startedAt(), finishedAt,
                run.stepSymbolsStatus(), stepPricesStatus, run.stepEvaluateStatus(), errorMessage);
    }

    private static EodPipelineRun withEvaluateStep(
            EodPipelineRun run,
            RunStatus status,
            StepStatus stepEvaluateStatus,
            Instant finishedAt,
            String errorMessage) {
        return new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), status, run.startedAt(), finishedAt,
                run.stepSymbolsStatus(), run.stepPricesStatus(), stepEvaluateStatus, errorMessage);
    }
}
