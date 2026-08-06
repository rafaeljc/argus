package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.RunId;

public final class StepInProgressException extends DomainException {

    private final RunId runId;
    private final PipelineStep step;

    public StepInProgressException(RunId runId, PipelineStep step) {
        super("eod pipeline step already in progress: run=" + runId.value() + " step=" + step.wireValue());
        this.runId = runId;
        this.step = step;
    }

    public RunId runId() {
        return runId;
    }

    public PipelineStep step() {
        return step;
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
