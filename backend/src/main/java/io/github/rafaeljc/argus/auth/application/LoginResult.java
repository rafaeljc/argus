package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.common.domain.UserId;

// The controller grants ROLE_ADMIN and establishes the Spring Session-backed Authentication —
// this record only carries what that decision needs.
public record LoginResult(UserId userId, boolean admin) {}
