package io.github.rafaeljc.argus.auth.web;

import jakarta.validation.constraints.NotBlank;

// No @Email on email: an invalid format on /auth/login should collapse into the same generic
// 401 that "unknown user" produces, not a distinguishable 422 field error. That preserves the
// anti-enumeration story on the login surface.
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {}
