package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.SessionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Logout {

    private final SessionRepository sessionRepository;

    public Logout(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void execute(SessionId sessionId) {
        sessionRepository.deleteById(sessionId);
    }
}
