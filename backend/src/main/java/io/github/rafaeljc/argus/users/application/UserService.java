package io.github.rafaeljc.argus.users.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Lazy
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public User lookup(UserId id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + id.value()));
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
    public User softDelete(UserId id) {
        User current = lookup(id);
        if (current.isDeleted()) {
            return current;
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

    public boolean verifyPassword(UserId id, String rawPassword) {
        User user = lookup(id);
        return passwordEncoder.matches(rawPassword, user.passwordHash());
    }
}
