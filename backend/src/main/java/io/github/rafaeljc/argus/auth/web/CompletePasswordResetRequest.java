package io.github.rafaeljc.argus.auth.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(
        @NotBlank String token,
        @JsonProperty("new_password") @NotBlank @Size(min = 8) String newPassword) {}
