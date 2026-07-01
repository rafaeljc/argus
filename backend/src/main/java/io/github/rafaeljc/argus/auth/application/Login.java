package io.github.rafaeljc.argus.auth.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.InvalidCredentialsException;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Login {

    private final UserService userService;
    private final SessionRepository sessionRepository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public Login(UserService userService, SessionRepository sessionRepository, Clock clock) {
        this.userService = userService;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.secureRandom = Tokens.strongSecureRandom();
    }

    @Transactional
    public LoginResult execute(String email, String password, String ipAddress, String userAgent) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // Anti-enumeration: every failed branch collapses into InvalidCredentialsException so
        // the response is indistinguishable between "unknown email", "wrong password", and
        // "email exists but not yet verified". The unknown-email branch also runs a dummy
        // Argon2id verify so its wall-clock cost matches the wrong-password branch, closing
        // the timing side-channel that would otherwise reveal account existence.
        Optional<User> maybeUser = userService.lookupActiveByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            userService.verifyPasswordForUnknownUser(password);
            throw new InvalidCredentialsException();
        }
        User user = maybeUser.get();

        if (!userService.verifyPassword(user.id(), password)) {
            throw new InvalidCredentialsException();
        }

        // Suspended is the one deliberate leak: the client needs to know it, and the surface is
        // already narrowed to "someone who knew the password".
        if (user.isSuspended()) {
            throw new AccountSuspendedException(user.id(), user.email());
        }

        if (!user.isVerified()) {
            throw new InvalidCredentialsException();
        }

        Instant now = clock.now();
        String plainSessionToken = Tokens.plain(secureRandom);
        String plainCsrfToken = Tokens.plain(secureRandom);
        Session persisted = sessionRepository.save(new Session(
                new SessionId(UuidCreator.getTimeOrderedEpoch()),
                user.id(),
                Tokens.sha256Hex(plainSessionToken),
                ipAddress,
                userAgent,
                now,
                now.plus(Session.ROLLING_WINDOW),
                now));

        return new LoginResult(
                persisted.id(),
                persisted.userId(),
                plainSessionToken,
                plainCsrfToken,
                persisted.expiresAt());
    }
}
