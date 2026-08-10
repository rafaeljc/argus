package io.github.rafaeljc.argus.admin.domain;

import io.github.rafaeljc.argus.common.domain.RunId;
import java.time.LocalDate;

public sealed interface AuditMetadata {

    record UserAction(String reason) implements AuditMetadata {
    }

    record EodRun(RunId runId, LocalDate runDate) implements AuditMetadata {
    }

    record EodStepRerun(RunId runId, String step) implements AuditMetadata {
    }
}
