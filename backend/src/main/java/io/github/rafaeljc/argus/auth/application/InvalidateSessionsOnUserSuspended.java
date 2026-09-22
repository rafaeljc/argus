package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.users.application.event.UserSuspended;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs synchronously inside UserLifecycleService.suspend's transaction, before commit: the
// suspended user's Redis sessions are dropped before the Postgres row flip commits. A Redis
// failure here rolls the suspend back; a later Postgres rollback only costs the user an extra
// re-login. AccountStateGateFilter re-checks state on every request regardless, so even a
// session that survives past this point is rejected on its next use.
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
