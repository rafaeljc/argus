package io.github.rafaeljc.argus.users.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    // Password used only to seed the anti-enumeration dummy hash below. It is never a login
    // credential — the seed is discarded after startup and only the resulting hash is retained.
    private static final String DUMMY_SEED = "anti-enumeration-dummy-seed";

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    // Pre-computed Argon2id hash with the same cost parameters as production user hashes.
    // Login runs a match against this hash when the email is unknown so that the total time
    // spent in verify() is indistinguishable from the wrong-password branch, closing the
    // timing side-channel that would otherwise reveal account existence.
    private final String dummyPasswordHash;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_SEED);
    }

    public User lookup(UserId id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + id.value()));
    }

    public User lookupActive(UserId id) {
        return repository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + id.value()));
    }

    // Returns empty when the email is unknown or belongs to a soft-deleted user. The caller
    // (e.g. Login) decides whether "not found" collapses into a generic 401 or a specific error.
    public Optional<User> lookupActiveByEmail(String email) {
        return repository.findActiveByEmail(email);
    }

    @Transactional
    public User createUnverified(String email, String rawPassword) {
        Instant now = clock.now();
        User user = new User(
                new UserId(UuidCreator.getTimeOrderedEpoch()),
                email,
                passwordEncoder.encode(rawPassword),
                false, false, false, false,
                now, now, null);
        return repository.save(user);
    }

    @Transactional
    public User markVerified(UserId id) {
        User current = lookup(id);
        if (current.isVerified()) {
            return current;
        }
        User verified = new User(
                current.id(),
                current.email(),
                current.passwordHash(),
                true,
                current.isSuspended(),
                current.isDeleted(),
                current.isAdmin(),
                current.createdAt(),
                clock.now(),
                current.deletedAt());
        return repository.save(verified);
    }

    @Transactional
    public User softDelete(UserId id, String rawPassword) {
        User current = lookupActive(id);
        if (!passwordEncoder.matches(rawPassword, current.passwordHash())) {
            throw new InvalidCurrentPasswordException(id);
        }
        Instant now = clock.now();
        User deleted = new User(
                current.id(),
                current.email(),
                current.passwordHash(),
                current.isVerified(),
                current.isSuspended(),
                true,
                current.isAdmin(),
                current.createdAt(),
                now,
                now);
        return repository.save(deleted);
    }

    // Suspension is enforced here rather than in each caller so every mutation path — password
    // reset today, account-settings change tomorrow — inherits the same policy without duplicating
    // the check. Deleted users are filtered up front by findActiveById.
    @Transactional
    public User updatePassword(UserId id, String rawPassword) {
        User current = lookupActive(id);
        if (current.isSuspended()) {
            throw new AccountSuspendedException(current.id(), current.email());
        }
        User updated = new User(
                current.id(),
                current.email(),
                passwordEncoder.encode(rawPassword),
                current.isVerified(),
                current.isSuspended(),
                current.isDeleted(),
                current.isAdmin(),
                current.createdAt(),
                clock.now(),
                current.deletedAt());
        return repository.save(updated);
    }

    public boolean verifyPassword(UserId id, String rawPassword) {
        User user = lookup(id);
        return passwordEncoder.matches(rawPassword, user.passwordHash());
    }

    // Runs an Argon2id verify against a canned hash so callers on the "unknown user" branch pay
    // the same wall-clock cost as the "wrong password" branch. Callers do not observe the
    // outcome — the dummy hash is chosen so it can never match a real password.
    public void verifyPasswordForUnknownUser(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }
}
