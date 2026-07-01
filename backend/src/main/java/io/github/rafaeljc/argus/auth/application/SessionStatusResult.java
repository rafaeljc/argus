package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public record SessionStatusResult(UserId userId, Instant expiresAt) {}
