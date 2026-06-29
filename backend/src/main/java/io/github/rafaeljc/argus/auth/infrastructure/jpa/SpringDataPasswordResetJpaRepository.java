package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPasswordResetJpaRepository extends JpaRepository<PasswordResetJpaEntity, UUID> {

    Optional<PasswordResetJpaEntity> findByTokenHash(String tokenHash);
}
