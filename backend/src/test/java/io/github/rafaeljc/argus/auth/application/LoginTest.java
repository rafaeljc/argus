package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.domain.InvalidCredentialsException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LoginTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher events;

    private Login login;

    @BeforeEach
    void setUp() {
        login = new Login(userService, events);
    }

    @Test
    void execute_validCredentials_returnsUserIdAndAdminFlag() {
        User verified = user(EMAIL, true, false, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(userService.verifyPassword(verified.id(), PASSWORD)).thenReturn(true);

        LoginResult result = login.execute(EMAIL, PASSWORD);

        assertThat(result.userId()).isEqualTo(verified.id());
        assertThat(result.admin()).isFalse();
    }

    @Test
    void execute_adminUser_returnsAdminFlagTrue() {
        User admin = user(EMAIL, true, false, true);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(userService.verifyPassword(admin.id(), PASSWORD)).thenReturn(true);

        LoginResult result = login.execute(EMAIL, PASSWORD);

        assertThat(result.admin()).isTrue();
    }

    @Test
    void execute_mixedCaseEmailWithWhitespace_isNormalizedForLookup() {
        User verified = user(EMAIL, true, false, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(userService.verifyPassword(verified.id(), PASSWORD)).thenReturn(true);

        login.execute("  Alice@Example.COM  ", PASSWORD);

        verify(userService).lookupActiveByEmail(EMAIL);
    }

    @Test
    void execute_unknownEmail_throwsInvalidCredentialsAndRunsDummyVerifyForTiming() {
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);

        // The unknown-email branch must still burn the same Argon2id cost as verifyPassword so
        // an attacker cannot distinguish "unknown email" from "wrong password" by timing.
        verify(userService).verifyPasswordForUnknownUser(PASSWORD);
        verify(userService, never()).verifyPassword(any(), any());
    }

    @Test
    void execute_wrongPassword_throwsInvalidCredentials() {
        User verified = user(EMAIL, true, false, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(userService.verifyPassword(verified.id(), PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void execute_suspendedUser_throwsAccountSuspended() {
        User suspended = user(EMAIL, true, true, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(suspended));
        when(userService.verifyPassword(suspended.id(), PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void execute_unverifiedUser_throwsInvalidCredentialsToPreventEnumeration() {
        User unverified = user(EMAIL, false, false, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(unverified));
        when(userService.verifyPassword(unverified.id(), PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> login.execute(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static User user(String email, boolean verified, boolean suspended, boolean admin) {
        return new User(
                new UserId(UUID.fromString(UuidCreator.getTimeOrderedEpoch().toString())),
                email,
                "argon2-hash-placeholder",
                verified,
                suspended,
                false,
                admin,
                FIXED_NOW, FIXED_NOW, null);
    }
}
