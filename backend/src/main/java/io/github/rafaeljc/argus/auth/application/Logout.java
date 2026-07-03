package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Logout {

    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher events;

    public Logout(SessionRepository sessionRepository, ApplicationEventPublisher events) {
        this.sessionRepository = sessionRepository;
        this.events = events;
    }

    @Transactional
    public void execute(SessionId sessionId, UserId userId) {
        sessionRepository.deleteById(sessionId);
        events.publishEvent(new AuthAuditEvent.LogoutSucceeded(userId));
    }
}
