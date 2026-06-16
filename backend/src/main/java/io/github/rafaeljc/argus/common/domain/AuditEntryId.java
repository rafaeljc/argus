package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record AuditEntryId(UUID value) {
    public AuditEntryId {
        if (value == null) {
            throw new IllegalArgumentException("AuditEntryId value must not be null");
        }
    }
}
