package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    Optional<SessionJpaEntity> findBySessionTokenHash(String sessionTokenHash);

    List<SessionJpaEntity> findByUserId(UUID userId);
}
