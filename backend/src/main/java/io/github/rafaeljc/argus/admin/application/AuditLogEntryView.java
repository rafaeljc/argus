package io.github.rafaeljc.argus.admin.application;

import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.Map;

// Read-side shape: metadata is passed through as the raw JSONB payload, not reconstructed into
// the sealed AuditMetadata used on the write path. The contract declares it as an opaque object,
// and passthrough renders any row regardless of which AuditMetadata variant (or none) wrote it.
public record AuditLogEntryView(
        AuditEntryId id, UserId actorId, AdminAction action, UserId targetUserId,
        Map<String, Object> metadata, Instant createdAt) {

    public AuditLogEntryView {
        if (id == null) {
            throw new IllegalArgumentException("AuditLogEntryView id must not be null");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("AuditLogEntryView actorId must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("AuditLogEntryView action must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("AuditLogEntryView createdAt must not be null");
        }
    }
}
