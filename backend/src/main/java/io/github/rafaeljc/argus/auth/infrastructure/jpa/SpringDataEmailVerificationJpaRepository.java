package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEmailVerificationJpaRepository extends JpaRepository<EmailVerificationJpaEntity, UUID> {

    Optional<EmailVerificationJpaEntity> findByTokenHash(String tokenHash);
}
