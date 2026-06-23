package io.github.rafaeljc.argus.users.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserAccountResponse(
        UUID id,
        String email,
        @JsonProperty("is_verified") boolean isVerified,
        @JsonProperty("is_admin") boolean isAdmin,
        @JsonProperty("created_at") Instant createdAt) {

    public static UserAccountResponse from(User user) {
        return new UserAccountResponse(
                user.id().value(),
                user.email(),
                user.isVerified(),
                user.isAdmin(),
                user.createdAt());
    }
}
