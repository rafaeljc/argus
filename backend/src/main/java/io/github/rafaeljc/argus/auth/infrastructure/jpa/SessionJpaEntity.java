package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
class SessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "session_token_hash", nullable = false, updatable = false)
    private String sessionTokenHash;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected SessionJpaEntity() {
        // Hibernate
    }

    SessionJpaEntity(UUID id,
                     UUID userId,
                     String sessionTokenHash,
                     String ipAddress,
                     String userAgent,
                     Instant createdAt,
                     Instant expiresAt,
                     Instant lastActivityAt) {
        this.id = id;
        this.userId = userId;
        this.sessionTokenHash = sessionTokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastActivityAt = lastActivityAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getSessionTokenHash() {
        return sessionTokenHash;
    }

    String getIpAddress() {
        return ipAddress;
    }

    String getUserAgent() {
        return userAgent;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
