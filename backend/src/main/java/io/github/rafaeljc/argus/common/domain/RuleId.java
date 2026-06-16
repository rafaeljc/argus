package io.github.rafaeljc.argus.common.domain;

import java.util.UUID;

public record RuleId(UUID value) {
    public RuleId {
        if (value == null) {
            throw new IllegalArgumentException("RuleId value must not be null");
        }
    }
}
