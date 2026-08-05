package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import java.time.LocalDate;

public final class RunAlreadyActiveException extends DomainException {

    private final LocalDate runDate;

    public RunAlreadyActiveException(LocalDate runDate) {
        super("eod pipeline run already active for date: " + runDate);
        this.runDate = runDate;
    }

    public LocalDate runDate() {
        return runDate;
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
