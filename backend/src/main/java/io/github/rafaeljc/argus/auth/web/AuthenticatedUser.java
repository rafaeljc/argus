package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

// The principal Spring Session persists inside the HttpSession. getName() returns the user id as
// a string so RateLimitFilter's per-user bucket key and the session index's PRINCIPAL_NAME_INDEX
// both key on it without an auth-module import. userId() backs @CurrentUserId's SpEL expression.
public record AuthenticatedUser(UUID id) implements Principal, Serializable {

    public AuthenticatedUser {
        if (id == null) {
            throw new IllegalArgumentException("AuthenticatedUser id must not be null");
        }
    }

    public UserId userId() {
        return new UserId(id);
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
