package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record RunId(UUID value) {
    public RunId {
        if (value == null) {
            throw new IllegalArgumentException("RunId value must not be null");
        }
    }
}
