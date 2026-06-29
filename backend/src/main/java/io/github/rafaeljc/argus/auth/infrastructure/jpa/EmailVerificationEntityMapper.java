package io.github.rafaeljc.argus.auth.infrastructure.jpa;

import io.github.rafaeljc.argus.auth.domain.EmailVerification;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.domain.VerificationId;

final class EmailVerificationEntityMapper {

    private EmailVerificationEntityMapper() {
    }

    static EmailVerification toDomain(EmailVerificationJpaEntity entity) {
        return new EmailVerification(
                new VerificationId(entity.getId()),
                new UserId(entity.getUserId()),
                entity.getTokenHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getVerifiedAt());
    }

    static EmailVerificationJpaEntity toEntity(EmailVerification verification) {
        return new EmailVerificationJpaEntity(
                verification.id().value(),
                verification.userId().value(),
                verification.tokenHash(),
                verification.createdAt(),
                verification.expiresAt(),
                verification.verifiedAt());
    }
}
