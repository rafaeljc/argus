package io.github.rafaeljc.argus.auth.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;

public interface SessionRepository {

    // Bulk invalidation for a single user (password reset, suspension, soft-delete, admin
    // reassignment). Session creation, resolution and per-request TTL refresh are owned by Spring
    // Session directly — this is the only session lifecycle operation the application layer
    // still needs a port for.
    void deleteAllForUser(UserId userId);
}
