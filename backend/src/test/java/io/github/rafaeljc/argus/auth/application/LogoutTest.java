package io.github.rafaeljc.argus.auth.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.SessionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutTest {

    @Mock
    private SessionRepository sessionRepository;

    @Test
    void execute_deletesSessionById() {
        Logout logout = new Logout(sessionRepository);
        SessionId sessionId = new SessionId(UuidCreator.getTimeOrderedEpoch());

        logout.execute(sessionId);

        verify(sessionRepository).deleteById(sessionId);
        verifyNoMoreInteractions(sessionRepository);
    }
}
