package io.github.rafaeljc.argus.auth.application.port;

import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(SessionId id);

    Optional<Session> findByTokenHash(String sessionTokenHash);

    List<Session> findByUserId(UserId userId);

    void touch(SessionId id, Instant lastActivityAt, Instant expiresAt, String ipAddress, String userAgent);

    void deleteById(SessionId id);

    // Bulk invalidation for a single user (password reset, admin-initiated logout-everywhere).
    // Preferred over findByUserId + per-row deleteById because it is one round trip and one SQL
    // statement, atomic under the caller's transaction.
    void deleteAllForUser(UserId userId);
}
