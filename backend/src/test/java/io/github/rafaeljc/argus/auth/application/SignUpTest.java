package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailAlreadyTakenException;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.common.domain.FieldError;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.domain.VerificationId;
import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SignUpTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Mock
    private UserService userService;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private ApplicationEventPublisher events;

    private FixedClock clock;
    private ObjectMapper objectMapper;
    private SignUp signUp;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        objectMapper = new ObjectMapper();
        signUp = new SignUp(
                userService, emailVerificationRepository, emailService, clock, objectMapper, events);
    }

    @Test
    void execute_validInputs_persistsUserAndEnqueuesVerificationEmail() throws Exception {
        User createdUser = newUser(EMAIL);
        when(userService.createUnverified(EMAIL, PASSWORD)).thenReturn(createdUser);
        when(emailVerificationRepository.save(any(EmailVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SignUpResult result = signUp.execute(EMAIL, PASSWORD);

        assertThat(result.userId()).isEqualTo(createdUser.id());
        assertThat(result.verificationSent()).isTrue();

        ArgumentCaptor<EmailVerification> verificationCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(verificationCaptor.capture());
        EmailVerification verification = verificationCaptor.getValue();
        assertThat(verification.userId()).isEqualTo(createdUser.id());
        assertThat(verification.tokenHash()).matches("[0-9a-f]{64}");
        assertThat(verification.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(verification.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofHours(24)));
        assertThat(verification.verifiedAt()).isNull();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> idempotenceKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enqueue(
                eq(EventType.VERIFICATION),
                eq(createdUser.id().value()),
                payloadCaptor.capture(),
                idempotenceKeyCaptor.capture());

        var payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("user_id").asString()).isEqualTo(createdUser.id().value().toString());
        assertThat(payload.get("email").asString()).isEqualTo(createdUser.email());
        assertThat(payload.get("token").asString()).matches("[A-Za-z0-9_-]+");
        assertThat(payload.get("expires_at").asString()).isEqualTo(verification.expiresAt().toString());
        // The hashed form persisted MUST differ from the plain token sent in the email.
        assertThat(payload.get("token").asString()).isNotEqualTo(verification.tokenHash());

        assertThat(idempotenceKeyCaptor.getValue())
                .isEqualTo("email.verification:" + verification.id().value());
    }

    // Locks the contract: translation depends on the constraint name substring, not on the
    // surrounding driver wording. Any Postgres/Hibernate version that reformats the message
    // will still trigger 422 as long as it names the index.
    @ParameterizedTest
    @ValueSource(strings = {
            "ERROR: duplicate key value violates unique constraint \"users_email_active_uidx\"",
            "duplicate key: users_email_active_uidx",
            "constraint [users_email_active_uidx] violated for key (email)=(alice@example.com)"
    })
    void execute_variedDuplicateEmailMessages_allTranslateToEmailAlreadyTaken(String driverMessage) {
        when(userService.createUnverified(EMAIL, PASSWORD))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement", new SQLException(driverMessage)));

        assertThatThrownBy(() -> signUp.execute(EMAIL, PASSWORD))
                .isInstanceOf(EmailAlreadyTakenException.class);

        verifyNoInteractions(emailVerificationRepository, emailService);
    }

    @Test
    void execute_unrelatedConstraintViolation_rethrowsAsIs() {
        DataIntegrityViolationException ex = uniqueViolation("some_other_index");
        when(userService.createUnverified(EMAIL, PASSWORD)).thenThrow(ex);

        assertThatThrownBy(() -> signUp.execute(EMAIL, PASSWORD))
                .isSameAs(ex);

        verifyNoInteractions(emailVerificationRepository, emailService);
    }

    @Test
    void emailAlreadyTakenException_carriesValidationErrorCodeAnd422AndFieldError() {
        EmailAlreadyTakenException ex = new EmailAlreadyTakenException(EMAIL);

        assertThat(ex.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).containsExactly(
                new FieldError("email", "already_taken", "Email already in use"));
        assertThat(ex.getMessage()).contains(EMAIL);
    }

    private User newUser(String email) {
        return new User(
                new UserId(UUID.randomUUID()),
                email,
                "argon2-hash-placeholder",
                false, false, false, false,
                FIXED_NOW, FIXED_NOW, null);
    }

    // Mirrors what the JDBC driver surfaces when a UNIQUE INDEX rejects an INSERT:
    // Spring's DataIntegrityViolationException wraps a driver SQLException whose message
    // includes the constraint (index) name. SignUp inspects that name to translate.
    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        SQLException cause = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"" + constraintName + "\"");
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
