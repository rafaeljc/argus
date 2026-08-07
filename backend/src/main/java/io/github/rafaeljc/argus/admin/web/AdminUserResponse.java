package io.github.rafaeljc.argus.admin.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        @JsonProperty("is_verified") boolean isVerified,
        @JsonProperty("is_suspended") boolean isSuspended,
        @JsonProperty("is_deleted") boolean isDeleted,
        @JsonProperty("is_admin") boolean isAdmin,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("deleted_at") Instant deletedAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.id().value(),
                user.email(),
                user.isVerified(),
                user.isSuspended(),
                user.isDeleted(),
                user.isAdmin(),
                user.createdAt(),
                user.deletedAt());
    }
}
