package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.users.application.event.UserSoftDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs synchronously inside UserService.softDelete's transaction: the deleted user's sessions
// are dropped in the same unit of work as the users-row flip, so a rollback of one rolls back
// the other and the browser's next request can no longer resolve a principal to the account.
@Component
public class InvalidateSessionsOnUserSoftDeleted {

    private final SessionRepository sessionRepository;

    public InvalidateSessionsOnUserSoftDeleted(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @EventListener
    public void on(UserSoftDeleted event) {
        sessionRepository.deleteAllForUser(event.userId());
    }
}
