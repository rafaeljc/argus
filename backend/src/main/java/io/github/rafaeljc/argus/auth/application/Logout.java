package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

// The actual session invalidation is handled by Spring Security's logout filter
// (SecurityContextLogoutHandler invalidates the HttpSession, deleting its Redis key); this use
// case is left with only the audit side effect.
@Service
public class Logout {

    private final ApplicationEventPublisher events;

    public Logout(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void execute(UserId userId) {
        events.publishEvent(new AuthAuditEvent.LogoutSucceeded(userId));
    }
}
