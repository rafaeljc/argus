package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record OutboxId(UUID value) {
    public OutboxId {
        if (value == null) {
            throw new IllegalArgumentException("OutboxId value must not be null");
        }
    }
}
