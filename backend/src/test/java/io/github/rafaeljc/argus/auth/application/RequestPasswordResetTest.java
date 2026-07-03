package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.auth.application.port.PasswordResetRepository;
import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.users.application.UserService;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RequestPasswordResetTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String EMAIL = "alice@example.com";

    @Mock
    private UserService userService;

    @Mock
    private PasswordResetRepository passwordResetRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ApplicationEventPublisher events;

    private FixedClock clock;
    private ObjectMapper objectMapper;
    private RequestPasswordReset requestPasswordReset;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        objectMapper = new ObjectMapper();
        requestPasswordReset = new RequestPasswordReset(
                userService, passwordResetRepository, emailService, clock, objectMapper, events);
    }

    @Test
    void execute_activeUser_persistsResetAndEnqueuesEmail() throws Exception {
        User user = newUser(EMAIL, false);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordResetRepository.save(any(PasswordReset.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        requestPasswordReset.execute(EMAIL);

        ArgumentCaptor<PasswordReset> resetCaptor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(passwordResetRepository).save(resetCaptor.capture());
        PasswordReset reset = resetCaptor.getValue();
        assertThat(reset.userId()).isEqualTo(user.id());
        assertThat(reset.tokenHash()).matches("[0-9a-f]{64}");
        assertThat(reset.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(reset.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofHours(1)));
        assertThat(reset.claimedAt()).isNull();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> idempotenceKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enqueue(
                eq(EventType.PASSWORD_RESET),
                eq(user.id().value()),
                payloadCaptor.capture(),
                idempotenceKeyCaptor.capture());

        var payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("user_id").asString()).isEqualTo(user.id().value().toString());
        assertThat(payload.get("email").asString()).isEqualTo(user.email());
        assertThat(payload.get("token").asString()).matches("[A-Za-z0-9_-]+");
        assertThat(payload.get("expires_at").asString()).isEqualTo(reset.expiresAt().toString());
        // The token in the outbox is the plain form (goes into the email link); DB stores only the hash.
        assertThat(payload.get("token").asString()).isNotEqualTo(reset.tokenHash());

        assertThat(idempotenceKeyCaptor.getValue())
                .isEqualTo("email.password_reset:" + reset.id().value());
    }

    @Test
    void execute_unknownEmail_silentlySucceedsWithoutPersistingOrEnqueueing() {
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.empty());

        requestPasswordReset.execute(EMAIL);

        verify(passwordResetRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void execute_suspendedUser_silentlySucceedsWithoutPersistingOrEnqueueing() {
        User suspended = newUser(EMAIL, true);
        when(userService.lookupActiveByEmail(EMAIL)).thenReturn(Optional.of(suspended));

        requestPasswordReset.execute(EMAIL);

        // Suspension leaks nothing at request-time: the response is 202-equivalent whether the
        // user is active, suspended, or does not exist. Prevents attackers from probing account
        // state via the reset endpoint and prevents mailing links to frozen accounts.
        verify(passwordResetRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    private static User newUser(String email, boolean suspended) {
        return new User(
                new UserId(UUID.randomUUID()),
                email,
                "argon2-hash-placeholder",
                true, suspended, false, false,
                FIXED_NOW, FIXED_NOW, null);
    }
}
