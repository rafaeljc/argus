package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunEvaluateStep {

    private static final Logger log = LoggerFactory.getLogger(RunEvaluateStep.class);
    private static final String LOCK_RESOURCE = "eod-pipeline-run";

    private final EodPipelineRunRepository runs;
    private final ActiveUserIds activeUserIds;
    private final WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts;
    private final TransactionalMutationLock lock;
    private final Clock clock;

    public RunEvaluateStep(
            EodPipelineRunRepository runs,
            ActiveUserIds activeUserIds,
            WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts,
            TransactionalMutationLock lock,
            Clock clock) {
        this.runs = runs;
        this.activeUserIds = activeUserIds;
        this.writeSnapshotAndEvaluateAlerts = writeSnapshotAndEvaluateAlerts;
        this.lock = lock;
        this.clock = clock;
    }

    // Same never-throws contract as RunSymbolsStep/RunPricesStep, but each active user's
    // snapshot+evaluate runs in its own transaction (WriteSnapshotAndEvaluateAlerts is
    // REQUIRES_NEW), so one user's failure never rolls back another user's already-committed
    // work; the step is failed/succeeded in aggregate afterward. @Transactional here for the same
    // reason as the other steps: RunAllSteps bypasses EodPipelineService entirely.
    @Transactional
    public EodPipelineRun execute(RunId id) {
        lock.acquireResourceById(LOCK_RESOURCE, id.value());

        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        RunStatus startedStatus = run.status() == RunStatus.PENDING ? RunStatus.IN_PROGRESS : run.status();
        EodPipelineRun started = withStep(run, startedStatus, StepStatus.IN_PROGRESS, null, null);
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
            EodPipelineRun succeeded = withStep(started, RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, null, null);
            runs.update(succeeded);
            return succeeded;
        }
        String message = "evaluate failed for %d of %d users".formatted(failures, userIds.size());
        EodPipelineRun failed = withStep(started, RunStatus.FAILED, StepStatus.FAILED, clock.now(), message);
        runs.update(failed);
        return failed;
    }

    private static EodPipelineRun withStep(
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
