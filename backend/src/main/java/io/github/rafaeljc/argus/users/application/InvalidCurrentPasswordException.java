package io.github.rafaeljc.argus.users.application;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.UserId;

public final class InvalidCurrentPasswordException extends DomainException {

    private final UserId userId;

    public InvalidCurrentPasswordException(UserId userId) {
        super("Current password is incorrect.");
        this.userId = userId;
    }

    public UserId userId() {
        return userId;
    }

    @Override
    public String code() {
        return "INVALID_CURRENT_PASSWORD";
    }
}
