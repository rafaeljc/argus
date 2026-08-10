package io.github.rafaeljc.argus.admin.domain;

public sealed interface AuditMetadata {

    record UserAction(String reason) implements AuditMetadata {
    }
}
