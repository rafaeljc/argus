package io.github.rafaeljc.argus.alerts.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        String direction,
        BigDecimal threshold,
        @JsonProperty("window_days") int windowDays,
        @JsonProperty("created_at") Instant createdAt) {

    public static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(
                rule.id().value(),
                rule.direction().name(),
                rule.threshold().value(),
                rule.window().days(),
                rule.createdAt());
    }
}
