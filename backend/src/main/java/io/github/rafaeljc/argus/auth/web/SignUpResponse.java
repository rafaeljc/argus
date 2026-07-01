package io.github.rafaeljc.argus.auth.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SignUpResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("verification_sent") boolean verificationSent) {}
