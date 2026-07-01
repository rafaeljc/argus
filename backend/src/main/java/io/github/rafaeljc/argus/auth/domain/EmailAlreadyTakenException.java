package io.github.rafaeljc.argus.auth.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.FieldError;
import java.util.List;

public final class EmailAlreadyTakenException extends DomainException {

    private static final List<FieldError> DETAILS =
            List.of(new FieldError("email", "already_taken", "Email already in use"));

    public EmailAlreadyTakenException(String email) {
        super("email already taken: " + email);
    }

    @Override
    public String code() {
        return "VALIDATION_ERROR";
    }

    @Override
    public List<FieldError> details() {
        return DETAILS;
    }
}
