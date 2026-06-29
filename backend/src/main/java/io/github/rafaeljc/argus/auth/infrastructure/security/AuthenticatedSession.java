package io.github.rafaeljc.argus.auth.infrastructure.security;

import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;

public record AuthenticatedSession(UserId userId, SessionId sessionId) {

    public AuthenticatedSession {
        if (userId == null) {
            throw new IllegalArgumentException("AuthenticatedSession userId must not be null");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("AuthenticatedSession sessionId must not be null");
        }
    }
}
