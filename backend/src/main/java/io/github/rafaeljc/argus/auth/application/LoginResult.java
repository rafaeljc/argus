package io.github.rafaeljc.argus.auth.application;

import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

// Return value of Login.execute. The plain sessionToken and csrfToken travel out to the
// controller once, land in Set-Cookie headers, and are never persisted; the DB holds only the
// SHA-256 hash of sessionToken (via Session.sessionTokenHash).
public record LoginResult(SessionId sessionId,
                          UserId userId,
                          String sessionToken,
                          String csrfToken,
                          Instant expiresAt) {}
