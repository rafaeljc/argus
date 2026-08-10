package io.github.rafaeljc.argus.admin.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.UUID;

public record AdminUserActionResponse(
        UUID id,
        @JsonProperty("is_suspended") boolean isSuspended,
        @JsonProperty("is_deleted") boolean isDeleted,
        @JsonProperty("deleted_at") Instant deletedAt) {

    public static AdminUserActionResponse from(User user) {
        return new AdminUserActionResponse(
                user.id().value(),
                user.isSuspended(),
                user.isDeleted(),
                user.deletedAt());
    }
}
