package io.github.rafaeljc.argus.eodpipeline.application.event;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;

// Published whenever an admin re-runs a pipeline step, so peer modules can react without
// eodpipeline depending on them. admin listens to write an audit row.
public record EodStepRerunTriggered(RunId runId, PipelineStep step, UserId actorId) {
}
