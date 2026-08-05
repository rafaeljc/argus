package io.github.rafaeljc.argus.eodpipeline.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EodPipelineRunResponse(
        @JsonProperty("run_id") UUID runId,
        @JsonProperty("run_date") LocalDate runDate,
        String trigger,
        String status,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("finished_at") Instant finishedAt,
        @JsonProperty("step_symbols_status") String stepSymbolsStatus,
        @JsonProperty("step_prices_status") String stepPricesStatus,
        @JsonProperty("step_evaluate_status") String stepEvaluateStatus,
        @JsonProperty("error_message") String errorMessage) {

    public static EodPipelineRunResponse from(EodPipelineRun run) {
        return new EodPipelineRunResponse(
                run.id().value(),
                run.runDate(),
                run.trigger().dbValue(),
                run.status().dbValue(),
                run.startedAt(),
                run.finishedAt(),
                run.stepSymbolsStatus().dbValue(),
                run.stepPricesStatus().dbValue(),
                run.stepEvaluateStatus().dbValue(),
                run.errorMessage());
    }
}
