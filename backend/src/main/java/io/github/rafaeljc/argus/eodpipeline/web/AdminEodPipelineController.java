package io.github.rafaeljc.argus.eodpipeline.web;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.eodpipeline.application.TriggerRun;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/admin/eod-pipeline/runs")
class AdminEodPipelineController {

    private final TriggerRun triggerRun;
    private final Clock clock;

    AdminEodPipelineController(TriggerRun triggerRun, Clock clock) {
        this.triggerRun = triggerRun;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<SuccessEnvelope<EodPipelineRunResponse>> trigger(
            @Valid @RequestBody(required = false) TriggerRunRequest body) {
        LocalDate runDate = (body != null && body.runDate() != null) ? body.runDate() : clock.today();
        EodPipelineRun run = triggerRun.execute(runDate, Trigger.ADMIN);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(run.id().value())
                        .toUri())
                .body(new SuccessEnvelope<>(EodPipelineRunResponse.from(run)));
    }
}
