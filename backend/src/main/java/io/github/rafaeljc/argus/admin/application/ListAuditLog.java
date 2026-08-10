package io.github.rafaeljc.argus.admin.application;

import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.common.application.PageResult;
import org.springframework.stereotype.Service;

@Service
public class ListAuditLog {

    private final AuditLogRepository auditLogRepository;

    public ListAuditLog(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public PageResult<AuditLogEntryView> list(AuditLogFilter filter, int page, int perPage) {
        return auditLogRepository.findFiltered(filter, page, perPage);
    }
}
