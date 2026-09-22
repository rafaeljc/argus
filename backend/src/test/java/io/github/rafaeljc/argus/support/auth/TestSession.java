package io.github.rafaeljc.argus.support.auth;

import io.github.rafaeljc.argus.common.domain.UserId;
import org.springframework.http.HttpHeaders;

// headers() carries the argus_session + argus_csrf Cookie header and the matching
// X-CSRF-Token header, ready to attach to an authenticated request.
public record TestSession(UserId userId, HttpHeaders headers) {}
