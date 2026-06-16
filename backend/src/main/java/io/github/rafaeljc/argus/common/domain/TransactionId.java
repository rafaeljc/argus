package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record TransactionId(UUID value) {
    public TransactionId {
        if (value == null) {
            throw new IllegalArgumentException("TransactionId value must not be null");
        }
    }
}
