package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record ResetId(UUID value) {
    public ResetId {
        if (value == null) {
            throw new IllegalArgumentException("ResetId value must not be null");
        }
    }
}
