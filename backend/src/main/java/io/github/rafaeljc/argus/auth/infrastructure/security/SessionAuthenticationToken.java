package io.github.rafaeljc.argus.auth.infrastructure.security;

import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public final class SessionAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedSession principal;

    public SessionAuthenticationToken(UserId userId, SessionId sessionId) {
        super(Collections.emptyList());
        this.principal = new AuthenticatedSession(userId, sessionId);
        // Authentication established by the session-resolution filter — trusted at construction.
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedSession getPrincipal() {
        return principal;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException(
                    "SessionAuthenticationToken is trusted at construction; use a new instance to authenticate");
        }
        super.setAuthenticated(false);
    }
}
