package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record JobId(UUID value) {
    public JobId {
        if (value == null) {
            throw new IllegalArgumentException("JobId value must not be null");
        }
    }
}
