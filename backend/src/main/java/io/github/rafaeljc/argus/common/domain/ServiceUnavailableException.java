package io.github.rafaeljc.argus.common.domain;

public final class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "SERVICE_UNAVAILABLE";
    }

    @Override
    public int status() {
        return 503;
    }
}
