package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
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
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunPricesStep {

    private static final String LOCK_RESOURCE = "eod-pipeline-run";

    private final EodPipelineRunRepository runs;
    private final ActiveUserIds activeUserIds;
    private final HeldTickers heldTickers;
    private final SyncDailyCloses syncDailyCloses;
    private final TransactionalMutationLock lock;
    private final Clock clock;

    public RunPricesStep(
            EodPipelineRunRepository runs,
            ActiveUserIds activeUserIds,
            HeldTickers heldTickers,
            SyncDailyCloses syncDailyCloses,
            TransactionalMutationLock lock,
            Clock clock) {
        this.runs = runs;
        this.activeUserIds = activeUserIds;
        this.heldTickers = heldTickers;
        this.syncDailyCloses = syncDailyCloses;
        this.lock = lock;
        this.clock = clock;
    }

    // Same never-throws contract as RunSymbolsStep. Independently callable: does not require
    // stepSymbolsStatus to have already succeeded — step ordering belongs to the trigger, and
    // the admin re-run endpoint invokes single steps by design. @Transactional here for the same
    // reason as RunSymbolsStep: RunAllSteps bypasses EodPipelineService entirely.
    @Transactional
    public EodPipelineRun execute(RunId id) {
        lock.acquireResourceById(LOCK_RESOURCE, id.value());

        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        RunStatus startedStatus = run.status() == RunStatus.PENDING ? RunStatus.IN_PROGRESS : run.status();
        EodPipelineRun started = withStep(run, startedStatus, StepStatus.IN_PROGRESS, null, null);
        runs.update(started);

        try {
            List<UserId> userIds = activeUserIds.find();
            Set<Ticker> tickers = heldTickers.findForUserIds(userIds);
            syncDailyCloses.sync(tickers, run.runDate());
            EodPipelineRun succeeded = withStep(started, RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, null, null);
            runs.update(succeeded);
            return succeeded;
        } catch (RuntimeException e) {
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            EodPipelineRun failed = withStep(started, RunStatus.FAILED, StepStatus.FAILED, clock.now(), message);
            runs.update(failed);
            return failed;
        }
    }

    private static EodPipelineRun withStep(
            EodPipelineRun run,
            RunStatus status,
            StepStatus stepPricesStatus,
            Instant finishedAt,
            String errorMessage) {
        return new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), status, run.startedAt(), finishedAt,
                run.stepSymbolsStatus(), stepPricesStatus, run.stepEvaluateStatus(), errorMessage);
    }
}
