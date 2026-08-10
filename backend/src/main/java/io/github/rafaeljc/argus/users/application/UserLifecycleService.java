package io.github.rafaeljc.argus.users.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.event.UserSoftDeleted;
import io.github.rafaeljc.argus.users.application.event.UserSuspended;
import io.github.rafaeljc.argus.users.application.port.UserLifecycle;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLifecycleService implements UserLifecycle {

    private final UserRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public UserLifecycleService(UserRepository repository, Clock clock, ApplicationEventPublisher events) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
    }

    @Override
    @Transactional
    public UserStateChange suspend(UserId id) {
        User current = lookup(id);
        if (current.isSuspended()) {
            return new UserStateChange(current, false);
        }
        User saved = repository.save(withFlags(current, true, current.isDeleted(), current.deletedAt()));
        events.publishEvent(new UserSuspended(saved.id()));
        return new UserStateChange(saved, true);
    }

    @Override
    @Transactional
    public UserStateChange unsuspend(UserId id) {
        User current = lookup(id);
        if (!current.isSuspended()) {
            return new UserStateChange(current, false);
        }
        User saved = repository.save(withFlags(current, false, current.isDeleted(), current.deletedAt()));
        return new UserStateChange(saved, true);
    }

    @Override
    @Transactional
    public UserStateChange softDelete(UserId id) {
        User current = lookup(id);
        if (current.isDeleted()) {
            return new UserStateChange(current, false);
        }
        Instant now = clock.now();
        User saved = repository.save(withFlags(current, current.isSuspended(), true, now));
        events.publishEvent(new UserSoftDeleted(saved.id()));
        return new UserStateChange(saved, true);
    }

    private User lookup(UserId id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user not found: " + id.value()));
    }

    private User withFlags(User current, boolean suspended, boolean deleted, Instant deletedAt) {
        return new User(
                current.id(),
                current.email(),
                current.passwordHash(),
                current.isVerified(),
                suspended,
                deleted,
                current.isAdmin(),
                current.createdAt(),
                clock.now(),
                deletedAt);
    }
}
