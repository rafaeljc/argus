package io.github.rafaeljc.argus.common.domain;

public final class SessionRequiredException extends DomainException {

    public SessionRequiredException() {
        super("session required");
    }

    @Override
    public String code() {
        return "UNAUTHORIZED";
    }

    @Override
    public int status() {
        return 401;
    }
}
