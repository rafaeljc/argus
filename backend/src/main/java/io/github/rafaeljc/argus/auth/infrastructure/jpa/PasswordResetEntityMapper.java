package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.domain.PasswordReset;
import io.github.rafaeljc.argus.common.domain.ResetId;
import io.github.rafaeljc.argus.common.domain.UserId;

final class PasswordResetEntityMapper {

    private PasswordResetEntityMapper() {
    }

    static PasswordReset toDomain(PasswordResetJpaEntity entity) {
        return new PasswordReset(
                new ResetId(entity.getId()),
                new UserId(entity.getUserId()),
                entity.getTokenHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getClaimedAt());
    }

    static PasswordResetJpaEntity toEntity(PasswordReset reset) {
        return new PasswordResetJpaEntity(
                reset.id().value(),
                reset.userId().value(),
                reset.tokenHash(),
                reset.createdAt(),
                reset.expiresAt(),
                reset.claimedAt());
    }
}
