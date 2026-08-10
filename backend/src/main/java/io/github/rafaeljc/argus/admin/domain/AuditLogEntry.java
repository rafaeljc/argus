package io.github.rafaeljc.argus.admin.domain;

import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;

public record AuditLogEntry(
        AuditEntryId id,
        UserId actorId,
        AdminAction action,
        UserId targetUserId,
        AuditMetadata metadata,
        Instant createdAt) {

    public AuditLogEntry {
        if (id == null) {
            throw new IllegalArgumentException("AuditLogEntry id must not be null");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("AuditLogEntry actorId must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("AuditLogEntry action must not be null");
        }
        if (action.requiresTargetUser() && targetUserId == null) {
            throw new IllegalArgumentException(
                    "AuditLogEntry targetUserId must not be null for action " + action);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("AuditLogEntry createdAt must not be null");
        }
    }
}
