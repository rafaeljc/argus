package io.github.rafaeljc.argus.eodpipeline.web;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CollectionEnvelope;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import io.github.rafaeljc.argus.eodpipeline.application.EodPipelineService;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/admin/eod-pipeline/runs")
class AdminEodPipelineController {

    private final EodPipelineService eodPipelineService;
    private final Clock clock;

    AdminEodPipelineController(EodPipelineService eodPipelineService, Clock clock) {
        this.eodPipelineService = eodPipelineService;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<SuccessEnvelope<EodPipelineRunResponse>> trigger(
            @Valid @RequestBody(required = false) TriggerRunRequest body) {
        LocalDate runDate = (body != null && body.runDate() != null) ? body.runDate() : clock.today();
        EodPipelineRun run = eodPipelineService.triggerPipelineRun(runDate, Trigger.ADMIN, currentAdminId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(run.id().value())
                        .toUri())
                .body(new SuccessEnvelope<>(EodPipelineRunResponse.from(run)));
    }

    @GetMapping
    ResponseEntity<CollectionEnvelope<EodPipelineRunResponse>> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(100_000) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage) {
        PageResult<EodPipelineRun> result = eodPipelineService.listRuns(page, perPage);
        int totalPages = result.totalPages();

        CollectionEnvelope.Meta meta =
                new CollectionEnvelope.Meta(result.total(), page, perPage, totalPages);
        CollectionEnvelope.Links links = new CollectionEnvelope.Links(
                pageUri(page, perPage),
                page < totalPages ? pageUri(page + 1, perPage) : null,
                page > 1 ? pageUri(page - 1, perPage) : null,
                pageUri(Math.max(totalPages, 1), perPage));

        return ResponseEntity.ok(new CollectionEnvelope<>(
                result.items().stream().map(EodPipelineRunResponse::from).toList(), meta, links));
    }

    @GetMapping("/{runId}")
    ResponseEntity<SuccessEnvelope<EodPipelineRunResponse>> get(@PathVariable UUID runId) {
        EodPipelineRun run = eodPipelineService.getRun(new RunId(runId));
        return ResponseEntity.ok(new SuccessEnvelope<>(EodPipelineRunResponse.from(run)));
    }

    @PostMapping("/{runId}/steps/{step}")
    ResponseEntity<SuccessEnvelope<EodStepResponse>> rerunFromStep(
            @PathVariable UUID runId, @PathVariable String step) {
        PipelineStep entryStep = PipelineStep.fromWireValue(step)
                .orElseThrow(() -> new ResourceNotFoundException("unknown eod pipeline step: " + step));
        EodPipelineRun run = eodPipelineService.rerunFromStep(new RunId(runId), entryStep, currentAdminId());
        return ResponseEntity.ok(new SuccessEnvelope<>(EodStepResponse.from(run, entryStep)));
    }

    private static UserId currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new UserId(UUID.fromString(auth.getName()));
    }

    private static String pageUri(int page, int perPage) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("per_page", perPage)
                .build()
                .toUriString();
    }
}
