package io.github.rafaeljc.argus.email.domain;

import io.github.rafaeljc.argus.common.domain.OutboxId;
import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        OutboxId id,
        UUID aggregateId,
        EventType eventType,
        String payload,
        String idempotenceKey,
        Instant createdAt,
        Instant publishedAt,
        int errorCount,
        String lastError,
        String publishedByWorkerId) {

    public OutboxMessage {
        if (id == null) {
            throw new IllegalArgumentException("OutboxMessage id must not be null");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("OutboxMessage aggregateId must not be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("OutboxMessage eventType must not be null");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("OutboxMessage payload must not be blank");
        }
        if (idempotenceKey == null || idempotenceKey.isBlank()) {
            throw new IllegalArgumentException("OutboxMessage idempotenceKey must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("OutboxMessage createdAt must not be null");
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("OutboxMessage errorCount must be >= 0");
        }
    }
}
