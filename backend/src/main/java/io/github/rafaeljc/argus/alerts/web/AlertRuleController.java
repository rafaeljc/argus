package io.github.rafaeljc.argus.alerts.web;

import io.github.rafaeljc.argus.alerts.application.AlertService;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CollectionEnvelope;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/alert-rules")
class AlertRuleController {

    private final AlertService alertService;

    AlertRuleController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    ResponseEntity<SuccessEnvelope<AlertRuleResponse>> create(
            @CurrentUserId UserId userId, @Valid @RequestBody CreateAlertRuleRequest body) {
        AlertRule saved = alertService.create(
                userId,
                body.direction(),
                new Percentage(body.threshold()),
                new AlertLookbackWindow(body.windowDays()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(saved.id().value())
                        .toUri())
                .body(new SuccessEnvelope<>(AlertRuleResponse.from(saved)));
    }

    @GetMapping
    ResponseEntity<CollectionEnvelope<AlertRuleResponse>> list(
            @CurrentUserId UserId userId,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(100_000) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage) {
        PageResult<AlertRule> result = alertService.listRules(userId, page, perPage);
        int totalPages = result.totalPages();

        CollectionEnvelope.Meta meta =
                new CollectionEnvelope.Meta(result.total(), page, perPage, totalPages);
        CollectionEnvelope.Links links = new CollectionEnvelope.Links(
                pageUri(page, perPage),
                page < totalPages ? pageUri(page + 1, perPage) : null,
                page > 1 ? pageUri(page - 1, perPage) : null,
                pageUri(Math.max(totalPages, 1), perPage));

        return ResponseEntity.ok(new CollectionEnvelope<>(
                result.items().stream().map(AlertRuleResponse::from).toList(), meta, links));
    }

    @GetMapping("/{id}")
    ResponseEntity<SuccessEnvelope<AlertRuleResponse>> get(@CurrentUserId UserId userId, @PathVariable UUID id) {
        AlertRule rule = alertService.getRule(userId, new RuleId(id));
        return ResponseEntity.ok(new SuccessEnvelope<>(AlertRuleResponse.from(rule)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId UserId userId, @PathVariable UUID id) {
        alertService.cancel(userId, new RuleId(id));
        return ResponseEntity.noContent().build();
    }

    private static String pageUri(int page, int perPage) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("per_page", perPage)
                .build()
                .toUriString();
    }
}
