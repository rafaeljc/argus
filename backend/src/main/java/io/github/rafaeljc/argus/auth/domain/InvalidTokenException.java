package io.github.rafaeljc.argus.auth.domain;

import io.github.rafaeljc.argus.common.domain.DomainException;

// Raised when an opaque single-use token (email verification, password reset) fails to redeem:
// unknown, expired, or already consumed. The three cases collapse to one wire code so callers
// cannot distinguish "no such token" from "expired" from "used" — that separation would let an
// attacker probe token existence and lifecycle without ever holding a valid token.
public final class InvalidTokenException extends DomainException {

    public InvalidTokenException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INVALID_TOKEN";
    }
}
