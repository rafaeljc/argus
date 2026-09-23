package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.auth.domain.InvalidCredentialsException;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditEvent;
import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class Login {

    private final UserService userService;
    private final ApplicationEventPublisher events;

    public Login(UserService userService, ApplicationEventPublisher events) {
        this.userService = userService;
        this.events = events;
    }

    public LoginResult execute(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            LoginResult result = attempt(normalizedEmail, password);
            events.publishEvent(new AuthAuditEvent.LoginSucceeded(result.userId(), normalizedEmail));
            return result;
        } catch (DomainException ex) {
            events.publishEvent(new AuthAuditEvent.LoginFailed(normalizedEmail, ex.code()));
            throw ex;
        }
    }

    // Anti-enumeration: every failed branch collapses into InvalidCredentialsException so
    // the response is indistinguishable between "unknown email", "wrong password", and
    // "email exists but not yet verified". The unknown-email branch also runs a dummy
    // Argon2id verify so its wall-clock cost matches the wrong-password branch, closing
    // the timing side-channel that would otherwise reveal account existence.
    private LoginResult attempt(String normalizedEmail, String password) {
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

        return new LoginResult(user.id(), user.isAdmin());
    }
}
