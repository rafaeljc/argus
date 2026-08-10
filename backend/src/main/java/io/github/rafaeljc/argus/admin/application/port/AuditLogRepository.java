package io.github.rafaeljc.argus.admin.application.port;

import io.github.rafaeljc.argus.admin.application.AuditLogEntryView;
import io.github.rafaeljc.argus.admin.application.AuditLogFilter;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.common.application.PageResult;

public interface AuditLogRepository {

    void insert(AuditLogEntry entry);

    PageResult<AuditLogEntryView> findFiltered(AuditLogFilter filter, int page, int perPage);
}
