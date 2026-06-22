package io.github.rafaeljc.argus.users.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.UserId;

public final class EmailNotVerifiedException extends DomainException {

    private final UserId userId;
    private final String email;

    public EmailNotVerifiedException(UserId userId, String email) {
        super("email not verified: " + userId.value());
        this.userId = userId;
        this.email = email;
    }

    public UserId userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    @Override
    public String code() {
        return "EMAIL_NOT_VERIFIED";
    }

    @Override
    public int status() {
        return 403;
    }
}
