package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.event.EodRunTriggered;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogAuditOnEodRunTriggeredTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void on_eodRunTriggered_insertsAuditRowWithRunMetadata() {
        LogAuditOnEodRunTriggered listener =
                new LogAuditOnEodRunTriggered(auditLogRepository, new FixedClock(NOW));
        RunId runId = new RunId(UuidCreator.getTimeOrderedEpoch());
        UserId actorId = new UserId(UuidCreator.getTimeOrderedEpoch());
        LocalDate runDate = LocalDate.of(2026, 6, 22);

        listener.on(new EodRunTriggered(runId, runDate, actorId));

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).insert(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.actorId()).isEqualTo(actorId);
        assertThat(entry.action()).isEqualTo(AdminAction.EOD_RUN);
        assertThat(entry.targetUserId()).isNull();
        assertThat(entry.metadata()).isEqualTo(new AuditMetadata.EodRun(runId, runDate));
        assertThat(entry.createdAt()).isEqualTo(NOW);
    }
}
