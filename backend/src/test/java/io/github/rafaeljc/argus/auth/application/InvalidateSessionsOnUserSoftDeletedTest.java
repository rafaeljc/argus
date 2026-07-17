package io.github.rafaeljc.argus.auth.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.event.UserSoftDeleted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvalidateSessionsOnUserSoftDeletedTest {

    @Mock
    private SessionRepository sessionRepository;

    @Test
    void on_userSoftDeleted_deletesAllSessionsForThatUser() {
        InvalidateSessionsOnUserSoftDeleted listener =
                new InvalidateSessionsOnUserSoftDeleted(sessionRepository);
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());

        listener.on(new UserSoftDeleted(userId));

        verify(sessionRepository).deleteAllForUser(userId);
        verifyNoMoreInteractions(sessionRepository);
    }
}
