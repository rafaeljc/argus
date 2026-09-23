package io.github.rafaeljc.argus.auth.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LogoutTest {

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void execute_publishesLogoutSucceeded() {
        Logout logout = new Logout(events);
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        logout.execute(userId);

        verify(events).publishEvent(new AuthAuditEvent.LogoutSucceeded(userId));
        verifyNoMoreInteractions(events);
    }
}
