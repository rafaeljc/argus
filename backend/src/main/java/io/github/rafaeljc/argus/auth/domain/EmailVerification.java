package io.github.rafaeljc.argus.auth.domain;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.domain.VerificationId;
import java.time.Instant;

public record EmailVerification(VerificationId id,
                                UserId userId,
                                String tokenHash,
                                Instant createdAt,
                                Instant expiresAt,
                                Instant verifiedAt) {

    public EmailVerification {
        if (id == null) {
            throw new IllegalArgumentException("EmailVerification id must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("EmailVerification userId must not be null");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("EmailVerification tokenHash must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("EmailVerification createdAt must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("EmailVerification expiresAt must not be null");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("EmailVerification expiresAt must be after createdAt");
        }
    }
}
