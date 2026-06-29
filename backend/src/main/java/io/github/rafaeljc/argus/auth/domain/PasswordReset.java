package io.github.rafaeljc.argus.auth.domain;

import io.github.rafaeljc.argus.common.domain.ResetId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public record PasswordReset(ResetId id,
                            UserId userId,
                            String tokenHash,
                            Instant createdAt,
                            Instant expiresAt,
                            Instant claimedAt) {

    public PasswordReset {
        if (id == null) {
            throw new IllegalArgumentException("PasswordReset id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("PasswordReset userId must not be null");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("PasswordReset tokenHash must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("PasswordReset createdAt must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("PasswordReset expiresAt must not be null");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("PasswordReset expiresAt must be after createdAt");
        }
    }
}
