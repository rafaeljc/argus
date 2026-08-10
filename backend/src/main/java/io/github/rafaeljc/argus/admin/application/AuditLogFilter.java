package io.github.rafaeljc.argus.admin.application;

import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

// All fields nullable: an absent filter means "don't restrict on this dimension".
// from/to are both inclusive of the boundary instant.
public record AuditLogFilter(UserId actorId, UserId targetUserId, AdminAction action, Instant from, Instant to) {

    public AuditLogFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("AuditLogFilter from must not be after to");
        }
    }
}
