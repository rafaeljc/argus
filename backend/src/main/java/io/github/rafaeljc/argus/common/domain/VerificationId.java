package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record VerificationId(UUID value) {
    public VerificationId {
        if (value == null) {
            throw new IllegalArgumentException("VerificationId value must not be null");
        }
    }
}
