package io.github.rafaeljc.argus.auth.domain;

import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Duration;
import java.time.Instant;

public record Session(SessionId id,
                      UserId userId,
                      String sessionTokenHash,
                      String ipAddress,
                      String userAgent,
                      Instant createdAt,
                      Instant expiresAt,
                      Instant lastActivityAt) {

    // Rolling session window: every request that resolves this session shifts expiresAt to
    // (now + ROLLING_WINDOW). Login sets the initial expiry; SessionResolutionFilter refreshes it.
    public static final Duration ROLLING_WINDOW = Duration.ofDays(30);

    public Session {
        if (id == null) {
            throw new IllegalArgumentException("Session id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Session userId must not be null");
        }
        if (sessionTokenHash == null || sessionTokenHash.isBlank()) {
            throw new IllegalArgumentException("Session sessionTokenHash must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Session createdAt must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Session expiresAt must not be null");
        }
        if (lastActivityAt == null) {
            throw new IllegalArgumentException("Session lastActivityAt must not be null");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Session expiresAt must be after createdAt");
        }
    }
}
