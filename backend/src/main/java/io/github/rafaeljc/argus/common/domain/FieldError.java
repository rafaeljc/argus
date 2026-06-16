package io.github.rafaeljc.argus.common.domain;

public record FieldError(String field, String code, String message) {

    public FieldError {
        if (field == null) {
            throw new IllegalArgumentException("FieldError field must not be null");
        }
        if (code == null) {
            throw new IllegalArgumentException("FieldError code must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("FieldError message must not be null");
        }
    }
}
