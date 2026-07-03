package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.InvalidCredentialsException;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LoginTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String IP = "203.0.113.7";
    private static final String USER_AGENT = "JUnit/5";

    @Mock
    private UserService userService;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ApplicationEventPublisher events;

    private FixedClock clock;
    private Login login;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        login = new Login(userService, sessionRepository, clock, events);
    }

    @Test
    void execute_validCredentials_persistsSessionAndReturnsPlainTokens() {
        User verified = user(EMAIL, true, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(userService.verifyPassword(verified.id(), PASSWORD)).thenReturn(true);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = login.execute(EMAIL, PASSWORD, IP, USER_AGENT);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session persisted = captor.getValue();
        assertThat(persisted.userId()).isEqualTo(verified.id());
        assertThat(persisted.sessionTokenHash()).matches("[0-9a-f]{64}");
        assertThat(persisted.ipAddress()).isEqualTo(IP);
        assertThat(persisted.userAgent()).isEqualTo(USER_AGENT);
        assertThat(persisted.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(persisted.lastActivityAt()).isEqualTo(FIXED_NOW);
        assertThat(persisted.expiresAt()).isEqualTo(FIXED_NOW.plus(Session.ROLLING_WINDOW));

        assertThat(result.sessionId()).isEqualTo(persisted.id());
        assertThat(result.userId()).isEqualTo(verified.id());
        assertThat(result.expiresAt()).isEqualTo(persisted.expiresAt());
        assertThat(result.sessionToken()).matches("[A-Za-z0-9_-]+");
        assertThat(result.csrfToken()).matches("[A-Za-z0-9_-]+");
        assertThat(result.sessionToken()).isNotEqualTo(result.csrfToken());
        // Plain session token must never equal the hash we persisted.
        assertThat(result.sessionToken()).isNotEqualTo(persisted.sessionTokenHash());
    }

    @Test
    void execute_mixedCaseEmailWithWhitespace_isNormalizedForLookup() {
        User verified = user(EMAIL, true, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(userService.verifyPassword(verified.id(), PASSWORD)).thenReturn(true);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        login.execute("  Alice@Example.COM  ", PASSWORD, IP, USER_AGENT);

        verify(userService).lookupActiveByEmail(EMAIL);
    }

    @Test
    void execute_unknownEmail_throwsInvalidCredentialsAndRunsDummyVerifyForTiming() {
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialsException.class);

        // The unknown-email branch must still burn the same Argon2id cost as verifyPassword so
        // an attacker cannot distinguish "unknown email" from "wrong password" by timing.
        verify(userService).verifyPasswordForUnknownUser(PASSWORD);
        verify(userService, never()).verifyPassword(any(), any());
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void execute_wrongPassword_throwsInvalidCredentialsAndDoesNotPersistSession() {
        User verified = user(EMAIL, true, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(userService.verifyPassword(verified.id(), PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void execute_suspendedUser_throwsAccountSuspendedAndDoesNotPersistSession() {
        User suspended = user(EMAIL, true, true);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(suspended));
        when(userService.verifyPassword(suspended.id(), PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD, IP, USER_AGENT))
                .isInstanceOf(AccountSuspendedException.class);

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void execute_unverifiedUser_throwsInvalidCredentialsToPreventEnumeration() {
        User unverified = user(EMAIL, false, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(unverified));
        when(userService.verifyPassword(unverified.id(), PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(sessionRepository);
    }

    private static User user(String email, boolean verified, boolean suspended) {
        return new User(
                new UserId(UUID.fromString(UuidCreator.getTimeOrderedEpoch().toString())),
                email,
                "argon2-hash-placeholder",
                verified,
                suspended,
                false,
                false,
                FIXED_NOW, FIXED_NOW, null);
    }
}
