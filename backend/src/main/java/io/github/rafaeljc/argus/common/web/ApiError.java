package io.github.rafaeljc.argus.common.web;

import io.github.rafaeljc.argus.common.domain.FieldError;
import java.util.List;

public record ApiError(String code, String message, List<FieldError> details) {

    public ApiError {
        if (code == null) {
            throw new IllegalArgumentException("ApiError code must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("ApiError message must not be null");
        }
        details = details == null ? List.of() : List.copyOf(details);
    }
}
