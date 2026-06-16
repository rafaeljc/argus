package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId value must not be null");
        }
    }
}
