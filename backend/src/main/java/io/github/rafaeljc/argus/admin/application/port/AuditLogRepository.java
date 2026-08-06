package io.github.rafaeljc.argus.admin.application.port;

import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;

public interface AuditLogRepository {

    void insert(AuditLogEntry entry);
}
