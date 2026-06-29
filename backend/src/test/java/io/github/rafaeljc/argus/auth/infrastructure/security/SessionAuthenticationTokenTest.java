package io.github.rafaeljc.argus.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.junit.jupiter.api.Test;

class SessionAuthenticationTokenTest {

    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final SessionId SESSION_ID = new SessionId(UuidCreator.getTimeOrderedEpoch());

    @Test
    void construct_validIds_exposesPrincipalAndAuthenticatedTrue() {
        SessionAuthenticationToken token = new SessionAuthenticationToken(USER_ID, SESSION_ID);

        AuthenticatedSession principal = (AuthenticatedSession) token.getPrincipal();
        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.sessionId()).isEqualTo(SESSION_ID);
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getCredentials()).isNull();
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void construct_nullUserId_throws() {
        assertThatThrownBy(() -> new SessionAuthenticationToken(null, SESSION_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_nullSessionId_throws() {
        assertThatThrownBy(() -> new SessionAuthenticationToken(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setAuthenticatedTrue_isRejected_perSpringSecurityContract() {
        SessionAuthenticationToken token = new SessionAuthenticationToken(USER_ID, SESSION_ID);

        assertThatThrownBy(() -> token.setAuthenticated(true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
