package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record SessionId(UUID value) {
    public SessionId {
        if (value == null) {
            throw new IllegalArgumentException("SessionId value must not be null");
        }
    }
}
