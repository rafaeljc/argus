package io.github.rafaeljc.argus.eodpipeline.domain;

import io.github.rafaeljc.argus.common.domain.RunId;
import java.time.Instant;
import java.time.LocalDate;

public record EodPipelineRun(RunId id,
                             LocalDate runDate,
                             Trigger trigger,
                             RunStatus status,
                             Instant startedAt,
                             Instant finishedAt,
                             StepStatus stepSymbolsStatus,
                             StepStatus stepPricesStatus,
                             StepStatus stepEvaluateStatus,
                             String errorMessage) {

    public EodPipelineRun {
        if (id == null) {
            throw new IllegalArgumentException("EodPipelineRun id must not be null");
        }
        if (runDate == null) {
            throw new IllegalArgumentException("EodPipelineRun runDate must not be null");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("EodPipelineRun trigger must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("EodPipelineRun status must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("EodPipelineRun startedAt must not be null");
        }
        if (stepSymbolsStatus == null) {
            throw new IllegalArgumentException("EodPipelineRun stepSymbolsStatus must not be null");
        }
        if (stepPricesStatus == null) {
            throw new IllegalArgumentException("EodPipelineRun stepPricesStatus must not be null");
        }
        if (stepEvaluateStatus == null) {
            throw new IllegalArgumentException("EodPipelineRun stepEvaluateStatus must not be null");
        }
        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("EodPipelineRun finishedAt must not be before startedAt");
        }
    }
}
