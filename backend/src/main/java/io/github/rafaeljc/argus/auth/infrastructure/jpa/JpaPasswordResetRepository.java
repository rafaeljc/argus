package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.application.port.PasswordResetRepository;
import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPasswordResetRepository implements PasswordResetRepository {

    private final SpringDataPasswordResetJpaRepository jpa;

    JpaPasswordResetRepository(SpringDataPasswordResetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PasswordReset save(PasswordReset reset) {
        PasswordResetJpaEntity persisted = jpa.save(PasswordResetEntityMapper.toEntity(reset));
        return PasswordResetEntityMapper.toDomain(persisted);
    }

    @Override
    public Optional<PasswordReset> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(PasswordResetEntityMapper::toDomain);
    }
}
