package io.github.rafaeljc.argus.users.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.domain.User;

final class UserEntityMapper {

    private UserEntityMapper() {
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(
                new UserId(entity.getId()),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isVerified(),
                entity.isSuspended(),
                entity.isDeleted(),
                entity.isAdmin(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id().value(),
                user.email(),
                user.passwordHash(),
                user.isVerified(),
                user.isSuspended(),
                user.isDeleted(),
                user.isAdmin(),
                user.createdAt(),
                user.updatedAt(),
                user.deletedAt());
    }
}
