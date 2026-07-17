package io.github.rafaeljc.argus.users.application.event;

import io.github.rafaeljc.argus.common.domain.UserId;

// Domain event published in UserService.softDelete's transaction. Peer modules subscribe to
// react to the deletion (auth invalidates the user's sessions, etc.). Kept separate from the
// AccountDeleted audit event so business side effects don't ride the observability channel.
public record UserSoftDeleted(UserId userId) {
}
