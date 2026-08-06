package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepInProgressException;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RerunFromStep {

    private static final String LOCK_RESOURCE = "eod-pipeline-run";

    private final EodPipelineRunRepository runs;
    private final RunDispatcher dispatcher;
    private final TransactionalMutationLock lock;

    public RerunFromStep(EodPipelineRunRepository runs, RunDispatcher dispatcher, TransactionalMutationLock lock) {
        this.runs = runs;
        this.dispatcher = dispatcher;
        this.lock = lock;
    }

    @Transactional
    public EodPipelineRun execute(RunId id, PipelineStep entryStep) {
        lock.acquireResourceById(LOCK_RESOURCE, id.value());

        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        // Any step in progress conflicts with a rerun, not just entryStep: eodPipelineTaskExecutor
        // is single-threaded, and the in-flight step's own completion write
        // (RunPricesStep.java etc.) would clobber whatever this rerun writes.
        if (run.stepSymbolsStatus() == StepStatus.IN_PROGRESS) {
            throw new StepInProgressException(id, PipelineStep.SYMBOLS);
        }
        if (run.stepPricesStatus() == StepStatus.IN_PROGRESS) {
            throw new StepInProgressException(id, PipelineStep.PRICES);
        }
        if (run.stepEvaluateStatus() == StepStatus.IN_PROGRESS) {
            throw new StepInProgressException(id, PipelineStep.EVALUATE);
        }

        EodPipelineRun reset = new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), RunStatus.IN_PROGRESS, run.startedAt(), null,
                stepStatusFor(PipelineStep.SYMBOLS, entryStep, run.stepSymbolsStatus()),
                stepStatusFor(PipelineStep.PRICES, entryStep, run.stepPricesStatus()),
                stepStatusFor(PipelineStep.EVALUATE, entryStep, run.stepEvaluateStatus()),
                null);
        EodPipelineRun updated = runs.update(reset);
        dispatcher.dispatchFrom(id, entryStep);
        return updated;
    }

    private static StepStatus stepStatusFor(PipelineStep step, PipelineStep entryStep, StepStatus current) {
        if (step == entryStep) {
            return StepStatus.IN_PROGRESS;
        }
        if (step.isAtOrAfter(entryStep)) {
            return StepStatus.PENDING;
        }
        return current;
    }
}
