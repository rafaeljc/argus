package io.github.rafaeljc.argus.users.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DeleteMyAccountRequest(
        @JsonProperty("current_password") @NotBlank String currentPassword) {}
