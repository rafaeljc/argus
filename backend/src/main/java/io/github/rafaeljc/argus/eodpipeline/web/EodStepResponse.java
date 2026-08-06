package io.github.rafaeljc.argus.eodpipeline.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import java.time.Instant;
import java.util.UUID;

public record EodStepResponse(
        @JsonProperty("run_id") UUID runId,
        String step,
        String status,
        @JsonProperty("started_at") Instant startedAt) {

    // There is no per-step timestamp column; started_at reflects the run's own startedAt.
    public static EodStepResponse from(EodPipelineRun run, PipelineStep step) {
        return new EodStepResponse(run.id().value(), step.wireValue(), run.status().dbValue(), run.startedAt());
    }
}
