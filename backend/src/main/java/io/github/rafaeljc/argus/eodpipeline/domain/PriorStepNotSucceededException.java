package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.RunId;

public final class PriorStepNotSucceededException extends DomainException {

    private final RunId runId;
    private final PipelineStep entryStep;
    private final PipelineStep blockingStep;
    private final StepStatus blockingStatus;

    public PriorStepNotSucceededException(
            RunId runId, PipelineStep entryStep, PipelineStep blockingStep, StepStatus blockingStatus) {
        super("cannot rerun eod pipeline from " + entryStep.wireValue() + ": run=" + runId.value()
                + " step=" + blockingStep.wireValue() + " status=" + blockingStatus.dbValue());
        this.runId = runId;
        this.entryStep = entryStep;
        this.blockingStep = blockingStep;
        this.blockingStatus = blockingStatus;
    }

    public RunId runId() {
        return runId;
    }

    public PipelineStep entryStep() {
        return entryStep;
    }

    public PipelineStep blockingStep() {
        return blockingStep;
    }

    public StepStatus blockingStatus() {
        return blockingStatus;
    }

    @Override
    public String code() {
        return "CONFLICT";
    }

    @Override
    public int status() {
        return 409;
    }
}
