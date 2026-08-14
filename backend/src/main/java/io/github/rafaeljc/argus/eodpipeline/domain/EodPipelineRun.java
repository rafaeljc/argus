package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.RunId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public record EodPipelineRun(RunId id,
                             LocalDate runDate,
                             Trigger trigger,
                             RunStatus status,
                             Instant startedAt,
                             Instant finishedAt,
                             StepStatus stepSymbolsStatus,
                             StepStatus stepPricesStatus,
                             StepStatus stepEvaluateStatus,
                             String errorMessage) {

    public EodPipelineRun {
        if (id == null) {
            throw new IllegalArgumentException("EodPipelineRun id must not be null");
        }
        if (runDate == null) {
            throw new IllegalArgumentException("EodPipelineRun runDate must not be null");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("EodPipelineRun trigger must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("EodPipelineRun status must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("EodPipelineRun startedAt must not be null");
        }
        if (stepSymbolsStatus == null) {
            throw new IllegalArgumentException("EodPipelineRun stepSymbolsStatus must not be null");
        }
        if (stepPricesStatus == null) {
            throw new IllegalArgumentException("EodPipelineRun stepPricesStatus must not be null");
        }
        if (stepEvaluateStatus == null) {
            throw new IllegalArgumentException("EodPipelineRun stepEvaluateStatus must not be null");
        }
        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("EodPipelineRun finishedAt must not be before startedAt");
        }
    }

    // At most one step of a run may be in progress at a time; this names the offender so a
    // rejected claim can say which step holds the run.
    public Optional<PipelineStep> stepInProgress() {
        for (PipelineStep step : PipelineStep.values()) {
            if (statusOf(step) == StepStatus.IN_PROGRESS) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    // Clears finishedAt and errorMessage: a step starting means the run is no longer settled,
    // and a stale message from a previous attempt would outlive the failure it described.
    public EodPipelineRun startingStep(PipelineStep step) {
        return withStep(step, StepStatus.IN_PROGRESS, RunStatus.IN_PROGRESS, null, null);
    }

    public EodPipelineRun withStepSucceeded(PipelineStep step) {
        return withStep(step, StepStatus.SUCCEEDED, RunStatus.IN_PROGRESS, null, null);
    }

    public EodPipelineRun withStepFailed(PipelineStep step, Instant finishedAt, String errorMessage) {
        return withStep(step, StepStatus.FAILED, RunStatus.FAILED, finishedAt, errorMessage);
    }

    public EodPipelineRun succeeded(Instant finishedAt) {
        return new EodPipelineRun(
                id, runDate, trigger, RunStatus.SUCCEEDED, startedAt, finishedAt,
                stepSymbolsStatus, stepPricesStatus, stepEvaluateStatus, errorMessage);
    }

    // Steps before entryStep keep whatever they last settled on — a rerun re-executes entryStep
    // and everything after it, and the earlier steps' results are still valid to keep only if they
    // actually succeeded; a failed or still-pending predecessor makes the transition invalid, so it
    // is rejected rather than built. entryStep is left pending rather than in_progress: it is
    // queued, not running, and claiming it here would make the executing worker's own claim
    // collide with it.
    public EodPipelineRun restartingFrom(PipelineStep entryStep) {
        Optional<PipelineStep> blocking = firstUnsucceededStepBefore(entryStep);
        if (blocking.isPresent()) {
            throw new PriorStepNotSucceededException(id, entryStep, blocking.get(), statusOf(blocking.get()));
        }
        return new EodPipelineRun(
                id, runDate, trigger, RunStatus.IN_PROGRESS, startedAt, null,
                restartedStatusOf(PipelineStep.SYMBOLS, entryStep),
                restartedStatusOf(PipelineStep.PRICES, entryStep),
                restartedStatusOf(PipelineStep.EVALUATE, entryStep),
                null);
    }

    private Optional<PipelineStep> firstUnsucceededStepBefore(PipelineStep entryStep) {
        for (PipelineStep step : PipelineStep.values()) {
            if (!step.isAtOrAfter(entryStep) && statusOf(step) != StepStatus.SUCCEEDED) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    private StepStatus restartedStatusOf(PipelineStep step, PipelineStep entryStep) {
        return step.isAtOrAfter(entryStep) ? StepStatus.PENDING : statusOf(step);
    }

    private StepStatus statusOf(PipelineStep step) {
        return switch (step) {
            case SYMBOLS -> stepSymbolsStatus;
            case PRICES -> stepPricesStatus;
            case EVALUATE -> stepEvaluateStatus;
        };
    }

    private EodPipelineRun withStep(
            PipelineStep step, StepStatus stepStatus, RunStatus runStatus, Instant finishedAt, String errorMessage) {
        return new EodPipelineRun(
                id, runDate, trigger, runStatus, startedAt, finishedAt,
                step == PipelineStep.SYMBOLS ? stepStatus : stepSymbolsStatus,
                step == PipelineStep.PRICES ? stepStatus : stepPricesStatus,
                step == PipelineStep.EVALUATE ? stepStatus : stepEvaluateStatus,
                errorMessage);
    }
}
