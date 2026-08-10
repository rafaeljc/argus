package io.github.rafaeljc.argus.admin.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.eodpipeline.application.event.EodStepRerunTriggered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Runs synchronously inside RerunFromStep.execute's transaction, so the audit row commits or
// rolls back with the step reset it describes.
@Component
public class LogAuditOnEodStepRerunTriggered {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public LogAuditOnEodStepRerunTriggered(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @EventListener
    public void on(EodStepRerunTriggered event) {
        auditLogRepository.insert(new AuditLogEntry(
                new AuditEntryId(UuidCreator.getTimeOrderedEpoch()),
                event.actorId(),
                AdminAction.EOD_STEP_RERUN,
                null,
                new AuditMetadata.EodStepRerun(event.runId(), event.step().wireValue()),
                clock.now()));
    }
}
