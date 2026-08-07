package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import org.springframework.stereotype.Service;

@Service
public class GetSessionStatus {

    private final SessionRepository sessionRepository;

    public GetSessionStatus(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // SessionResolutionFilter has already refreshed expiresAt for this request; the row we
    // load here is the freshly-touched state, which is what the SPA renders on bootstrap.
    public SessionStatusResult execute(SessionId sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow(SessionRequiredException::new);
        return new SessionStatusResult(session.userId(), session.expiresAt());
    }
}
