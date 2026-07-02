package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.auth.domain.InvalidTokenException;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.domain.VerificationId;
import io.github.rafaeljc.argus.users.application.UserService;
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

@ExtendWith(MockitoExtension.class)
class VerifyEmailTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String PLAIN_TOKEN = "some-plain-token";

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private UserService userService;

    private FixedClock clock;
    private VerifyEmail verifyEmail;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        verifyEmail = new VerifyEmail(emailVerificationRepository, userService, clock);
    }

    @Test
    void execute_validNonExpiredToken_marksVerificationAndUser() {
        EmailVerification stored = newVerification(tokenHash(PLAIN_TOKEN), FIXED_NOW.plus(Duration.ofHours(1)), null);
        when(emailVerificationRepository.findByTokenHash(tokenHash(PLAIN_TOKEN)))
                .thenReturn(Optional.of(stored));
        when(emailVerificationRepository.save(any(EmailVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        verifyEmail.execute(PLAIN_TOKEN);

        ArgumentCaptor<EmailVerification> savedCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(savedCaptor.capture());
        EmailVerification saved = savedCaptor.getValue();
        assertThat(saved.id()).isEqualTo(stored.id());
        assertThat(saved.userId()).isEqualTo(stored.userId());
        assertThat(saved.tokenHash()).isEqualTo(stored.tokenHash());
        assertThat(saved.createdAt()).isEqualTo(stored.createdAt());
        assertThat(saved.expiresAt()).isEqualTo(stored.expiresAt());
        assertThat(saved.verifiedAt()).isEqualTo(FIXED_NOW);

        verify(userService).markVerified(stored.userId());
    }

    @Test
    void execute_alreadyUsedToken_throwsInvalidTokenAndDoesNotWrite() {
        EmailVerification consumed = newVerification(
                tokenHash(PLAIN_TOKEN),
                FIXED_NOW.plus(Duration.ofHours(1)),
                FIXED_NOW.minus(Duration.ofMinutes(5)));
        when(emailVerificationRepository.findByTokenHash(tokenHash(PLAIN_TOKEN)))
                .thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> verifyEmail.execute(PLAIN_TOKEN))
                .isInstanceOf(InvalidTokenException.class);

        verify(emailVerificationRepository, never()).save(any());
        verifyNoInteractions(userService);
    }

    @Test
    void execute_expiredToken_throwsInvalidTokenAndDoesNotWrite() {
        EmailVerification expired = newVerification(
                tokenHash(PLAIN_TOKEN),
                FIXED_NOW.minus(Duration.ofSeconds(1)),
                null);
        when(emailVerificationRepository.findByTokenHash(tokenHash(PLAIN_TOKEN)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> verifyEmail.execute(PLAIN_TOKEN))
                .isInstanceOf(InvalidTokenException.class);

        verify(emailVerificationRepository, never()).save(any());
        verifyNoInteractions(userService);
    }

    @Test
    void execute_unknownToken_throwsInvalidTokenAndDoesNotWrite() {
        when(emailVerificationRepository.findByTokenHash(tokenHash(PLAIN_TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyEmail.execute(PLAIN_TOKEN))
                .isInstanceOf(InvalidTokenException.class);

        verify(emailVerificationRepository, never()).save(any());
        verifyNoInteractions(userService);
    }

    @Test
    void invalidTokenException_carriesInvalidTokenCodeAnd422() {
        InvalidTokenException ex = new InvalidTokenException("nope");

        assertThat(ex.code()).isEqualTo("INVALID_TOKEN");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEmpty();
    }

    private static String tokenHash(String plain) {
        return Tokens.sha256Hex(plain);
    }

    private static EmailVerification newVerification(String tokenHash, Instant expiresAt, Instant verifiedAt) {
        return new EmailVerification(
                new VerificationId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                tokenHash,
                FIXED_NOW.minus(Duration.ofHours(1)),
                expiresAt,
                verifiedAt);
    }
}
