package io.github.rafaeljc.argus.users.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringDataUserJpaRepository jpa;

    JpaUserRepository(SpringDataUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findActiveById(UserId id) {
        return jpa.findByIdAndDeletedFalse(id.value()).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findActiveByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return jpa.findByEmailAndDeletedFalse(normalized).map(UserEntityMapper::toDomain);
    }

    @Override
    public User save(User user) {
        // saveAndFlush forces the INSERT/UPDATE now so constraint violations
        // (e.g. users_email_active_uidx on signup) surface as a
        // DataIntegrityViolationException from this call rather than at commit,
        // letting the caller translate them into a domain exception.
        UserJpaEntity persisted = jpa.saveAndFlush(UserEntityMapper.toEntity(user));
        return UserEntityMapper.toDomain(persisted);
    }
}
