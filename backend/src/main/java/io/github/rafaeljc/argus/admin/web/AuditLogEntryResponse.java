package io.github.rafaeljc.argus.admin.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.admin.application.AuditLogEntryView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogEntryResponse(
        UUID id,
        @JsonProperty("actor_id") UUID actorId,
        String action,
        @JsonProperty("target_user_id") UUID targetUserId,
        Map<String, Object> metadata,
        @JsonProperty("created_at") Instant createdAt) {

    public static AuditLogEntryResponse from(AuditLogEntryView view) {
        return new AuditLogEntryResponse(
                view.id().value(),
                view.actorId().value(),
                view.action().dbValue(),
                view.targetUserId() == null ? null : view.targetUserId().value(),
                view.metadata(),
                view.createdAt());
    }
}
