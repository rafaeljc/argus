package io.github.rafaeljc.argus.users.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "is_suspended", nullable = false)
    private boolean suspended;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserJpaEntity() {
        // Hibernate
    }

    UserJpaEntity(UUID id,
                  String email,
                  String passwordHash,
                  boolean verified,
                  boolean suspended,
                  boolean deleted,
                  boolean admin,
                  Instant createdAt,
                  Instant updatedAt,
                  Instant deletedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.verified = verified;
        this.suspended = suspended;
        this.deleted = deleted;
        this.admin = admin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    boolean isVerified() {
        return verified;
    }

    boolean isSuspended() {
        return suspended;
    }

    boolean isDeleted() {
        return deleted;
    }

    boolean isAdmin() {
        return admin;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }
}
