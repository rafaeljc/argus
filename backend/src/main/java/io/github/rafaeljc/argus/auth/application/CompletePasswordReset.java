package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.application.port.PasswordResetRepository;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.InvalidTokenException;
import io.github.rafaeljc.argus.auth.domain.PasswordReset;
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
public class CompletePasswordReset {

    private final UserService userService;
    private final PasswordResetRepository passwordResetRepository;
    private final SessionRepository sessionRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public CompletePasswordReset(UserService userService,
                                 PasswordResetRepository passwordResetRepository,
                                 SessionRepository sessionRepository,
                                 Clock clock,
                                 ApplicationEventPublisher events) {
        this.userService = userService;
        this.passwordResetRepository = passwordResetRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.events = events;
    }

    // Unknown / expired / used all collapse to INVALID_TOKEN so callers cannot probe token
    // lifecycle without a valid token. Single-use is enforced by the read-then-write inside this
    // transaction (see VerifyEmail). No new session is issued — the user re-authenticates via
    // /auth/login, keeping future MFA / device / geo policies attached to one entry point.
    @Transactional
    public void execute(String plainToken, String newPassword) {
        try {
            User updated = redeem(plainToken, newPassword);
            events.publishEvent(new AuthAuditEvent.PasswordResetCompleted(updated.id(), updated.email()));
        } catch (DomainException ex) {
            events.publishEvent(new AuthAuditEvent.PasswordResetCompletionFailed(ex.code()));
            throw ex;
        }
    }

    private User redeem(String plainToken, String newPassword) {
        String tokenHash = Tokens.sha256Hex(plainToken);
        PasswordReset stored = passwordResetRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("password reset token not found"));

        if (stored.claimedAt() != null) {
            throw new InvalidTokenException("password reset token already used");
        }

        Instant now = clock.now();
        if (now.isAfter(stored.expiresAt())) {
            throw new InvalidTokenException("password reset token expired");
        }

        // Suspension is enforced inside UserService.updatePassword — a suspended user cannot have
        // their account mutated by any caller. On AccountSuspendedException the remaining steps
        // below never execute, so the token is left unclaimed (still redeemable if the account is
        // un-suspended within its 1h TTL) and no sessions are touched.
        User updated = userService.updatePassword(stored.userId(), newPassword);

        PasswordReset claimed = new PasswordReset(
                stored.id(),
                stored.userId(),
                stored.tokenHash(),
                stored.createdAt(),
                stored.expiresAt(),
                now);
        passwordResetRepository.save(claimed);

        // A password change is a trust event: every prior session predates the new credential and
        // must not survive it. Bulk delete keeps this to one round trip regardless of session count.
        sessionRepository.deleteAllForUser(stored.userId());
        return updated;
    }
}
