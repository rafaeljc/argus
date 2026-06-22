package io.github.rafaeljc.argus.users.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.UserId;

public final class AccountSuspendedException extends DomainException {

    private final UserId userId;
    private final String email;

    public AccountSuspendedException(UserId userId, String email) {
        super("account suspended: " + userId.value());
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
        return "ACCOUNT_SUSPENDED";
    }

    @Override
    public int status() {
        return 403;
    }
}
