package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.users.application.event.UserSuspended;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs synchronously inside UserLifecycleService.suspend's transaction: the suspended user's
// sessions are dropped in the same unit of work as the users-row flip, so a rollback of one
// rolls back the other and the browser's next request can no longer resolve a principal.
@Component
public class InvalidateSessionsOnUserSuspended {

    private final SessionRepository sessionRepository;

    public InvalidateSessionsOnUserSuspended(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @EventListener
    public void on(UserSuspended event) {
        sessionRepository.deleteAllForUser(event.userId());
    }
}
