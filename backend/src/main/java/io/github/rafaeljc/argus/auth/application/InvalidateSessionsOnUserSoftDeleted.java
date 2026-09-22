package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.users.application.event.UserSoftDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs synchronously inside UserService.softDelete's transaction, before commit: the deleted
// user's Redis sessions are dropped before the Postgres row flip commits. A Redis failure here
// rolls the delete back; a later Postgres rollback only costs the user an extra re-login.
// AccountStateGateFilter re-checks state on every request regardless, so even a session that
// survives past this point is rejected on its next use.
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
