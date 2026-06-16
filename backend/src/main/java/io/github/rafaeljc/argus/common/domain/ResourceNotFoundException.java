package io.github.rafaeljc.argus.common.domain;

public final class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }

    @Override
    public int status() {
        return 404;
    }
}
