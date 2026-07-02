package io.github.rafaeljc.argus.auth.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.PasswordResetRepository;
import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResetId;
import io.github.rafaeljc.argus.email.application.EmailService;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RequestPasswordReset {

    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserService userService;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailService emailService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public RequestPasswordReset(UserService userService,
                                PasswordResetRepository passwordResetRepository,
                                EmailService emailService,
                                Clock clock,
                                ObjectMapper objectMapper) {
        this.userService = userService;
        this.passwordResetRepository = passwordResetRepository;
        this.emailService = emailService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.secureRandom = Tokens.strongSecureRandom();
    }

    // Unknown and suspended users take the same silent branch as a successful request. The wire
    // response is always 202 regardless of account state, so the reset endpoint cannot be used to
    // probe which addresses are registered or which accounts are frozen. Suspended accounts also
    // must not receive reset links: mailing a frozen account is confusing at best and undermines
    // the freeze at worst.
    // TODO: NFR-Sec7 — the silent branches skip token generation, the DB insert, and the outbox
    // insert, leaving a wall-clock delta an attacker can measure to enumerate registered
    // addresses. Login closes the analogous gap via UserService.verifyPasswordForUnknownUser.
    @Transactional
    public void execute(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> maybeUser = userService.lookupActiveByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();
        if (user.isSuspended()) {
            return;
        }

        String plainToken = Tokens.plain(secureRandom);
        PasswordReset reset = persistResetToken(user, Tokens.sha256Hex(plainToken));
        enqueueResetEmail(user, reset, plainToken);
    }

    private PasswordReset persistResetToken(User user, String tokenHash) {
        Instant now = clock.now();
        PasswordReset reset = new PasswordReset(
                new ResetId(UuidCreator.getTimeOrderedEpoch()),
                user.id(),
                tokenHash,
                now,
                now.plus(RESET_TTL),
                null);
        return passwordResetRepository.save(reset);
    }

    private void enqueueResetEmail(User user, PasswordReset reset, String plainToken) {
        Map<String, String> payload = Map.of(
                "user_id", user.id().value().toString(),
                "email", user.email(),
                "token", plainToken,
                "expires_at", reset.expiresAt().toString());
        String serialized = objectMapper.writeValueAsString(payload);
        String idempotenceKey = "email.password_reset:" + reset.id().value();
        emailService.enqueue(EventType.PASSWORD_RESET, user.id().value(), serialized, idempotenceKey);
    }
}
