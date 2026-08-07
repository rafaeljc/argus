package io.github.rafaeljc.argus.users.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataUserJpaRepository
        extends JpaRepository<UserJpaEntity, UUID>, JpaSpecificationExecutor<UserJpaEntity> {

    Optional<UserJpaEntity> findByEmailAndDeletedFalse(String email);

    Optional<UserJpaEntity> findByIdAndDeletedFalse(UUID id);

    List<UserJpaEntity> findByDeletedFalseAndSuspendedFalse();
}
