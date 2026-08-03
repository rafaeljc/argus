package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.marketdata.application.SyncSymbolUniverse;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EodPipelineService {

    private final EodPipelineRunRepository runs;
    private final SyncSymbolUniverse syncSymbolUniverse;
    private final Clock clock;

    public EodPipelineService(EodPipelineRunRepository runs, SyncSymbolUniverse syncSymbolUniverse, Clock clock) {
        this.runs = runs;
        this.syncSymbolUniverse = syncSymbolUniverse;
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
}
