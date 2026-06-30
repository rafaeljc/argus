package io.github.rafaeljc.argus.common.domain;

import java.util.List;
import java.util.Map;

public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String code();

    public int status() {
        return 422;
    }

    public List<FieldError> details() {
        return List.of();
    }

    public Map<String, String> headers() {
        return Map.of();
    }
}
