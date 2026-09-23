package io.github.rafaeljc.argus.users.application.event;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;

// Published in EnsureSoleAdmin's transaction whenever makeSoleAdmin actually flips a row: the
// newly granted admin plus any admin demoted alongside it. Authorities are granted at login and
// cached in the session for its lifetime, so every affected user's sessions must be invalidated —
// otherwise a demoted admin keeps ROLE_ADMIN, and a freshly granted one waits out their existing
// session before the grant takes effect.
public record AdminAssignmentChanged(List<UserId> userIds) {
}
