package io.github.rafaeljc.argus.auth.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailAlreadyTakenException;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.VerificationId;
import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SignUp {

    // Partial unique index on users(email) WHERE is_deleted = FALSE — enforces one active
    // account per email. Postgres surfaces the index name inside the driver's error message.
    private static final String EMAIL_UNIQUE_INDEX = "users_email_active_uidx";

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);

    private final UserService userService;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final SecureRandom secureRandom;

    public SignUp(UserService userService,
                  EmailVerificationRepository emailVerificationRepository,
                  EmailService emailService,
                  Clock clock,
                  ObjectMapper objectMapper,
                  ApplicationEventPublisher events) {
        this.userService = userService;
        this.emailVerificationRepository = emailVerificationRepository;
        this.emailService = emailService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.events = events;
        this.secureRandom = Tokens.strongSecureRandom();
    }

    @Transactional
    public SignUpResult execute(String email, String password) {
        try {
            User user = createUser(email, password);
            String plainToken = Tokens.plain(secureRandom);
            EmailVerification verification = persistVerificationToken(user, Tokens.sha256Hex(plainToken));
            enqueueVerificationEmail(user, verification, plainToken);
            events.publishEvent(new AuthAuditEvent.SignupSucceeded(user.id(), user.email()));
            return new SignUpResult(user.id(), true);
        } catch (DomainException ex) {
            events.publishEvent(new AuthAuditEvent.SignupFailed(email, ex.code()));
            throw ex;
        }
    }

    private User createUser(String email, String password) {
        try {
            return userService.createUnverified(email, password);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw new EmailAlreadyTakenException(email);
            }
            throw ex;
        }
    }

    private EmailVerification persistVerificationToken(User user, String tokenHash) {
        Instant now = clock.now();
        EmailVerification verification = new EmailVerification(
                new VerificationId(UuidCreator.getTimeOrderedEpoch()),
                user.id(),
                tokenHash,
                now,
                now.plus(VERIFICATION_TTL),
                null);
        return emailVerificationRepository.save(verification);
    }

    private void enqueueVerificationEmail(User user, EmailVerification verification, String plainToken) {
        Map<String, String> payload = Map.of(
                "user_id", user.id().value().toString(),
                "email", user.email(),
                "token", plainToken,
                "expires_at", verification.expiresAt().toString());
        String serialized = objectMapper.writeValueAsString(payload);
        String idempotenceKey = "email.verification:" + verification.id().value();
        emailService.enqueue(EventType.VERIFICATION, user.id().value(), serialized, idempotenceKey);
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? ex.getMessage() : cause.getMessage();
        return message != null && message.contains(EMAIL_UNIQUE_INDEX);
    }
}
