package io.github.rafaeljc.argus.auth.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

// Wire shape of EnvelopeSessionResult.data in contracts/openapi/argus-v1.yaml. Reused by both
// POST /auth/login and GET /auth/status — both return the current user's id and the rolling
// session expiry.
public record SessionResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("expires_at") Instant expiresAt) {}
