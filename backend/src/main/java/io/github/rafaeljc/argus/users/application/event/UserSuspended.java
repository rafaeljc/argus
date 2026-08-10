package io.github.rafaeljc.argus.users.application.event;

import io.github.rafaeljc.argus.common.domain.UserId;

// Twin of UserSoftDeleted: published in UserLifecycleService.suspend's transaction so peer
// modules (auth invalidating sessions) react in the same unit of work as the flag flip.
public record UserSuspended(UserId userId) {
}
