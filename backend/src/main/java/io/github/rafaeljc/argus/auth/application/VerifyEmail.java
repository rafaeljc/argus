package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.EmailVerificationRepository;
import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.auth.domain.InvalidTokenException;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerifyEmail {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserService userService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public VerifyEmail(EmailVerificationRepository emailVerificationRepository,
                       UserService userService,
                       Clock clock,
                       ApplicationEventPublisher events) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.userService = userService;
        this.clock = clock;
        this.events = events;
    }

    // Single-use is enforced by the read-then-write inside this transaction: two concurrent
    // redeems of the same token race on the row; the second either sees verifiedAt already set
    // and throws, or is serialized behind the first commit. Unknown / expired / used all collapse
    // to INVALID_TOKEN so callers cannot probe lifecycle without a valid token.
    @Transactional
    public void execute(String plainToken) {
        try {
            User verified = redeem(plainToken);
            events.publishEvent(new AuthAuditEvent.EmailVerified(verified.id(), verified.email()));
        } catch (DomainException ex) {
            events.publishEvent(new AuthAuditEvent.EmailVerificationFailed(ex.code()));
            throw ex;
        }
    }

    private User redeem(String plainToken) {
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

        return userService.markVerified(stored.userId());
    }
}
