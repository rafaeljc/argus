package io.github.rafaeljc.argus.auth.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;

// Thrown by Login for every failed authentication branch — unknown email, wrong password, or
// unverified account. One generic 401 UNAUTHORIZED hides which of the three failed, which is
// what closes the account-enumeration surface on the login endpoint.
public final class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("invalid email or password");
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
