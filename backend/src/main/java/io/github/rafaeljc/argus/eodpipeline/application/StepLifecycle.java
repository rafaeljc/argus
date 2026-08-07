package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.StepInProgressException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The two ends of a step, each its own short transaction so that StepExecution can run the step's
// work between them without a transaction open. Separate from StepExecution because a self-call
// from there would bypass the Spring proxy and collapse both back into one transaction.
@Service
public class StepLifecycle {

    private final EodPipelineRunRepository runs;
    private final Clock clock;

    public StepLifecycle(EodPipelineRunRepository runs, Clock clock) {
        this.runs = runs;
        this.clock = clock;
    }

    // Commits the claim before returning, which is what makes the in_progress visible to the next
    // claimant. The guard is the UPDATE's own WHERE clause, so a loser is rejected here and now
    // rather than queued behind whoever is running.
    @Transactional
    public EodPipelineRun claim(RunId id, PipelineStep step) {
        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        return runs.updateIfNoStepInProgress(run.startingStep(step))
                .orElseThrow(() -> new StepInProgressException(id, stepHoldingRun(id, step)));
    }

    @Transactional
    public EodPipelineRun settle(EodPipelineRun claimed, PipelineStep step, StepOutcome outcome) {
        EodPipelineRun settled = outcome.succeeded()
                ? claimed.withStepSucceeded(step)
                : claimed.withStepFailed(step, clock.now(), outcome.errorMessage());
        return runs.update(settled);
    }

    // Re-read on the rejected path only: the state that beat us was written after the read above,
    // so the run has to be looked at again to name the step in the conflict.
    private PipelineStep stepHoldingRun(RunId id, PipelineStep attempted) {
        return runs.findById(id)
                .flatMap(EodPipelineRun::stepInProgress)
                .orElse(attempted);
    }
}
