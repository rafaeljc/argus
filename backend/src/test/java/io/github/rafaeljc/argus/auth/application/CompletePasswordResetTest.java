package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.auth.application.port.PasswordResetRepository;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.InvalidTokenException;
import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResetId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Duration;
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
class CompletePasswordResetTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String PLAIN_TOKEN = "some-plain-token";
    private static final String NEW_PASSWORD = "shiny-new-passphrase";

    @Mock
    private UserService userService;

    @Mock
    private PasswordResetRepository passwordResetRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ApplicationEventPublisher events;

    private FixedClock clock;
    private CompletePasswordReset completePasswordReset;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        completePasswordReset = new CompletePasswordReset(
                userService, passwordResetRepository, sessionRepository, clock, events);
    }

    @Test
    void execute_validToken_updatesPasswordMarksClaimedAndInvalidatesSessionsForUser() {
        UserId userId = new UserId(UUID.randomUUID());
        PasswordReset stored = newReset(userId, Tokens.sha256Hex(PLAIN_TOKEN),
                FIXED_NOW.plus(Duration.ofMinutes(30)), null);
        when(passwordResetRepository.findByTokenHash(Tokens.sha256Hex(PLAIN_TOKEN)))
                .thenReturn(Optional.of(stored));
        when(passwordResetRepository.save(any(PasswordReset.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userService.updatePassword(userId, NEW_PASSWORD)).thenReturn(activeUser(userId));

        completePasswordReset.execute(PLAIN_TOKEN, NEW_PASSWORD);

        verify(userService).updatePassword(userId, NEW_PASSWORD);

        ArgumentCaptor<PasswordReset> resetCaptor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(passwordResetRepository).save(resetCaptor.capture());
        PasswordReset saved = resetCaptor.getValue();
        assertThat(saved.id()).isEqualTo(stored.id());
        assertThat(saved.userId()).isEqualTo(stored.userId());
        assertThat(saved.tokenHash()).isEqualTo(stored.tokenHash());
        assertThat(saved.createdAt()).isEqualTo(stored.createdAt());
        assertThat(saved.expiresAt()).isEqualTo(stored.expiresAt());
        assertThat(saved.claimedAt()).isEqualTo(FIXED_NOW);

        verify(sessionRepository).deleteAllForUser(userId);
    }

    @Test
    void execute_unknownToken_throwsInvalidTokenAndDoesNotWrite() {
        when(passwordResetRepository.findByTokenHash(Tokens.sha256Hex(PLAIN_TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> completePasswordReset.execute(PLAIN_TOKEN, NEW_PASSWORD))
                .isInstanceOf(InvalidTokenException.class);

        verify(passwordResetRepository, never()).save(any());
        verifyNoInteractions(userService, sessionRepository);
    }

    @Test
    void execute_expiredToken_throwsInvalidTokenAndDoesNotWrite() {
        UserId userId = new UserId(UUID.randomUUID());
        PasswordReset expired = newReset(userId, Tokens.sha256Hex(PLAIN_TOKEN),
                FIXED_NOW.minus(Duration.ofSeconds(1)), null);
        when(passwordResetRepository.findByTokenHash(Tokens.sha256Hex(PLAIN_TOKEN)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> completePasswordReset.execute(PLAIN_TOKEN, NEW_PASSWORD))
                .isInstanceOf(InvalidTokenException.class);

        verify(passwordResetRepository, never()).save(any());
        verifyNoInteractions(userService, sessionRepository);
    }

    @Test
    void execute_alreadyClaimedToken_throwsInvalidTokenAndDoesNotWrite() {
        UserId userId = new UserId(UUID.randomUUID());
        PasswordReset claimed = newReset(userId, Tokens.sha256Hex(PLAIN_TOKEN),
                FIXED_NOW.plus(Duration.ofMinutes(30)),
                FIXED_NOW.minus(Duration.ofMinutes(5)));
        when(passwordResetRepository.findByTokenHash(Tokens.sha256Hex(PLAIN_TOKEN)))
                .thenReturn(Optional.of(claimed));

        assertThatThrownBy(() -> completePasswordReset.execute(PLAIN_TOKEN, NEW_PASSWORD))
                .isInstanceOf(InvalidTokenException.class);

        verify(passwordResetRepository, never()).save(any());
        verifyNoInteractions(userService, sessionRepository);
    }

    @Test
    void execute_suspendedUser_propagatesAccountSuspendedFromUserService() {
        UserId userId = new UserId(UUID.randomUUID());
        PasswordReset stored = newReset(userId, Tokens.sha256Hex(PLAIN_TOKEN),
                FIXED_NOW.plus(Duration.ofMinutes(30)), null);
        when(passwordResetRepository.findByTokenHash(Tokens.sha256Hex(PLAIN_TOKEN)))
                .thenReturn(Optional.of(stored));
        // The suspension gate lives inside UserService.updatePassword so every mutation path
        // inherits it. CompletePasswordReset only needs to propagate.
        AccountSuspendedException suspension = new AccountSuspendedException(userId, "alice@example.com");
        org.mockito.Mockito.doThrow(suspension).when(userService).updatePassword(userId, NEW_PASSWORD);

        assertThatThrownBy(() -> completePasswordReset.execute(PLAIN_TOKEN, NEW_PASSWORD))
                .isSameAs(suspension);

        verify(passwordResetRepository, never()).save(any());
        verify(sessionRepository, never()).deleteAllForUser(any());
    }

    private static PasswordReset newReset(UserId userId, String tokenHash, Instant expiresAt, Instant claimedAt) {
        return new PasswordReset(
                new ResetId(UUID.randomUUID()),
                userId,
                tokenHash,
                FIXED_NOW.minus(Duration.ofHours(1)),
                expiresAt,
                claimedAt);
    }

    private static User activeUser(UserId id) {
        return new User(id, "alice@example.com", "hash", true, false, false, false,
                FIXED_NOW, FIXED_NOW, null);
    }
}
