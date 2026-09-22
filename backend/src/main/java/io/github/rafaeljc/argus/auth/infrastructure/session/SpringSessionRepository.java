package io.github.rafaeljc.argus.auth.infrastructure.session;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Repository;

@Repository
class SpringSessionRepository implements SessionRepository {

    private final FindByIndexNameSessionRepository<?> sessions;

    SpringSessionRepository(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void deleteAllForUser(UserId userId) {
        sessions.findByPrincipalName(userId.value().toString())
                .keySet()
                .forEach(sessions::deleteById);
    }
}
