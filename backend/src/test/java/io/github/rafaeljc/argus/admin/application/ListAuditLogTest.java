package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.common.application.PageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAuditLogTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private ListAuditLog listAuditLog;

    @BeforeEach
    void setUp() {
        listAuditLog = new ListAuditLog(auditLogRepository);
    }

    @Test
    void list_delegatesFilterAndPagingToPort_returnsPortResultUnchanged() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null);
        PageResult<AuditLogEntryView> expected = new PageResult<>(List.of(), 0, 2, 25);
        when(auditLogRepository.findFiltered(filter, 2, 25)).thenReturn(expected);

        PageResult<AuditLogEntryView> result = listAuditLog.list(filter, 2, 25);

        assertThat(result).isSameAs(expected);
    }
}
