package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;

final class SessionEntityMapper {

    private SessionEntityMapper() {
    }

    static Session toDomain(SessionJpaEntity entity) {
        return new Session(
                new SessionId(entity.getId()),
                new UserId(entity.getUserId()),
                entity.getSessionTokenHash(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getLastActivityAt());
    }

    static SessionJpaEntity toEntity(Session session) {
        return new SessionJpaEntity(
                session.id().value(),
                session.userId().value(),
                session.sessionTokenHash(),
                session.ipAddress(),
                session.userAgent(),
                session.createdAt(),
                session.expiresAt(),
                session.lastActivityAt());
    }
}
