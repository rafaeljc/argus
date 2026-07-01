package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSessionStatusTest {

    private static final Instant CREATED = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant EXPIRES = CREATED.plus(Session.ROLLING_WINDOW);

    @Mock
    private SessionRepository sessionRepository;

    @Test
    void execute_existingSession_returnsUserIdAndExpiresAt() {
        SessionId sessionId = new SessionId(UuidCreator.getTimeOrderedEpoch());
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        Session session = new Session(
                sessionId, userId, "0".repeat(64), null, null, CREATED, EXPIRES, CREATED);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionStatusResult result = new GetSessionStatus(sessionRepository).execute(sessionId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    void execute_sessionMissing_throwsSessionRequired() {
        SessionId sessionId = new SessionId(UuidCreator.getTimeOrderedEpoch());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetSessionStatus(sessionRepository).execute(sessionId))
                .isInstanceOf(SessionRequiredException.class);
    }
}
