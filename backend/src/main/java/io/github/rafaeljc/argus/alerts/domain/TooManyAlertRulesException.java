package io.github.rafaeljc.argus.alerts.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;

public final class TooManyAlertRulesException extends DomainException {

    private final int limit;

    public TooManyAlertRulesException(int limit) {
        super("too many active alert rules: limit is " + limit);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }

    @Override
    public String code() {
        return "TOO_MANY_RULES";
    }
}
