package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.RunId;

public final class RunNotSettledException extends DomainException {

    private final RunId runId;
    private final RunStatus runStatus;

    public RunNotSettledException(RunId runId, RunStatus runStatus) {
        super("eod pipeline run has not settled: run=" + runId.value() + " status=" + runStatus.dbValue());
        this.runId = runId;
        this.runStatus = runStatus;
    }

    public RunId runId() {
        return runId;
    }

    public RunStatus runStatus() {
        return runStatus;
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
