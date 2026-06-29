package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSessionRepository implements SessionRepository {

    private final SpringDataSessionJpaRepository jpa;

    JpaSessionRepository(SpringDataSessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Session save(Session session) {
        SessionJpaEntity persisted = jpa.save(SessionEntityMapper.toEntity(session));
        return SessionEntityMapper.toDomain(persisted);
    }

    @Override
    public Optional<Session> findByTokenHash(String sessionTokenHash) {
        return jpa.findBySessionTokenHash(sessionTokenHash).map(SessionEntityMapper::toDomain);
    }

    @Override
    public List<Session> findByUserId(UserId userId) {
        return jpa.findByUserId(userId.value()).stream().map(SessionEntityMapper::toDomain).toList();
    }
}
