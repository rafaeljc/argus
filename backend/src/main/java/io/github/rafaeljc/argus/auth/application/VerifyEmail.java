package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.auth.domain.InvalidTokenException;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.users.application.UserService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerifyEmail {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserService userService;
    private final Clock clock;

    public VerifyEmail(EmailVerificationRepository emailVerificationRepository,
                       UserService userService,
                       Clock clock) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.userService = userService;
        this.clock = clock;
    }

    // Single-use is enforced by the read-then-write inside this transaction: two concurrent
    // redeems of the same token race on the row; the second either sees verifiedAt already set
    // and throws, or is serialized behind the first commit. Unknown / expired / used all collapse
    // to INVALID_TOKEN so callers cannot probe lifecycle without a valid token.
    @Transactional
    public void execute(String plainToken) {
        String tokenHash = Tokens.sha256Hex(plainToken);
        EmailVerification stored = emailVerificationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("email verification token not found"));

        if (stored.verifiedAt() != null) {
            throw new InvalidTokenException("email verification token already used");
        }

        Instant now = clock.now();
        if (!stored.expiresAt().isAfter(now)) {
            throw new InvalidTokenException("email verification token expired");
        }

        EmailVerification consumed = new EmailVerification(
                stored.id(),
                stored.userId(),
                stored.tokenHash(),
                stored.createdAt(),
                stored.expiresAt(),
                now);
        emailVerificationRepository.save(consumed);

        userService.markVerified(stored.userId());
    }
}
