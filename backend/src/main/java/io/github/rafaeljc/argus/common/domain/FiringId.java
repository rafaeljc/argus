package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record FiringId(UUID value) {
    public FiringId {
        if (value == null) {
            throw new IllegalArgumentException("FiringId value must not be null");
        }
    }
}
