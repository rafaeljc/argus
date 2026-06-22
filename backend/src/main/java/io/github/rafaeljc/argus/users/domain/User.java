package io.github.rafaeljc.argus.users.domain;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

public final class User {

    private static final int EMAIL_MAX_LENGTH = 254;

    // RFC 5321 shape, deliberately permissive: one '@', no whitespace, at least one dot in the
    // domain. Strict syntactic conformance is delegated to the email-verification flow — the
    // only authoritative test that an address is real is that someone reads the verification mail.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserId id;
    private final String email;
    private final String passwordHash;
    private final boolean verified;
    private final boolean suspended;
    private final boolean deleted;
    private final boolean admin;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    public User(UserId id,
                String email,
                String passwordHash,
                boolean verified,
                boolean suspended,
                boolean deleted,
                boolean admin,
                Instant createdAt,
                Instant updatedAt,
                Instant deletedAt) {
        if (id == null) {
            throw new IllegalArgumentException("User id must not be null");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("User passwordHash must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("User createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("User updatedAt must not be null");
        }
        if (deleted && deletedAt == null) {
            throw new IllegalArgumentException(
                    "User deletedAt must be set when isDeleted is true");
        }
        if (!deleted && deletedAt != null) {
            throw new IllegalArgumentException(
                    "User deletedAt must be null when isDeleted is false");
        }

        this.id = id;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.verified = verified;
        this.suspended = suspended;
        this.deleted = deleted;
        this.admin = admin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    private static String normalizeEmail(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("User email must not be null");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("User email must not be blank");
        }
        if (normalized.length() > EMAIL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "User email must be at most " + EMAIL_MAX_LENGTH + " characters");
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("User email is malformed");
        }
        return normalized;
    }

    public UserId id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isAdmin() {
        return admin;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }
}
