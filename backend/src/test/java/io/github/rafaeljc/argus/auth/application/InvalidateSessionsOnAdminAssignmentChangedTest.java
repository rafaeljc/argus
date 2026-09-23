package io.github.rafaeljc.argus.auth.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.event.AdminAssignmentChanged;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvalidateSessionsOnAdminAssignmentChangedTest {

    @Mock
    private SessionRepository sessionRepository;

    private static UserId newUserId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    void on_adminAssignmentChanged_deletesAllSessionsForEveryAffectedUser() {
        InvalidateSessionsOnAdminAssignmentChanged listener =
                new InvalidateSessionsOnAdminAssignmentChanged(sessionRepository);
        UserId granted = newUserId();
        UserId demoted = newUserId();

        listener.on(new AdminAssignmentChanged(List.of(granted, demoted)));

        verify(sessionRepository).deleteAllForUser(granted);
        verify(sessionRepository).deleteAllForUser(demoted);
        verifyNoMoreInteractions(sessionRepository);
    }
}
