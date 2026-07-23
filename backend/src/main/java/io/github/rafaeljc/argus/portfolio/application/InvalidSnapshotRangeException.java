package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.DomainException;

public final class InvalidSnapshotRangeException extends DomainException {

    public InvalidSnapshotRangeException(String range) {
        super("unknown snapshot range: " + range);
    }

    @Override
    public String code() {
        return "VALIDATION_ERROR";
    }
}
