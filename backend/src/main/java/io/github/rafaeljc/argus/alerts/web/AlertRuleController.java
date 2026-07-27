package io.github.rafaeljc.argus.alerts.web;

import io.github.rafaeljc.argus.alerts.application.AlertService;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.common.web.CurrentUserId;
import io.github.rafaeljc.argus.common.web.SuccessEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId UserId userId, @PathVariable UUID id) {
        alertService.cancel(userId, new RuleId(id));
        return ResponseEntity.noContent().build();
    }
}
