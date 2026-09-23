package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.users.application.event.AdminAssignmentChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs synchronously inside EnsureSoleAdmin's transaction, before commit. Authorities are granted
// at login and cached in the session for its lifetime, so every user whose is_admin flag just
// flipped needs their sessions dropped: a demoted admin must not keep ROLE_ADMIN, and a freshly
// granted one should not have to wait out an existing session before the grant takes effect.
@Component
public class InvalidateSessionsOnAdminAssignmentChanged {

    private final SessionRepository sessionRepository;

    public InvalidateSessionsOnAdminAssignmentChanged(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @EventListener
    public void on(AdminAssignmentChanged event) {
        event.userIds().forEach(sessionRepository::deleteAllForUser);
    }
}
