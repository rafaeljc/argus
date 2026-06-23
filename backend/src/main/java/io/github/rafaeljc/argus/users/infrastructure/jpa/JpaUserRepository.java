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
    public Optional<User> findActiveByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return jpa.findByEmailAndDeletedFalse(normalized).map(UserEntityMapper::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity persisted = jpa.save(UserEntityMapper.toEntity(user));
        return UserEntityMapper.toDomain(persisted);
    }
}
