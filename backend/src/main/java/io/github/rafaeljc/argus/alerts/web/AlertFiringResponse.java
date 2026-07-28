package io.github.rafaeljc.argus.alerts.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AlertFiringResponse(
        UUID id,
        @JsonProperty("rule_id") UUID ruleId,
        String direction,
        BigDecimal threshold,
        @JsonProperty("window_days") int windowDays,
        @JsonProperty("fired_at") Instant firedAt,
        @JsonProperty("portfolio_value_start") String portfolioValueStart,
        @JsonProperty("portfolio_value_end") String portfolioValueEnd,
        @JsonProperty("percent_change") BigDecimal percentChange,
        @JsonProperty("window_start_date") LocalDate windowStartDate,
        @JsonProperty("window_end_date") LocalDate windowEndDate) {

    public static AlertFiringResponse from(AlertFiring firing) {
        return new AlertFiringResponse(
                firing.id().value(),
                firing.ruleId().value(),
                firing.direction().name(),
                firing.threshold().value(),
                firing.window().days(),
                firing.firedAt(),
                firing.portfolioValueStart().value().toPlainString(),
                firing.portfolioValueEnd().value().toPlainString(),
                firing.percentChange(),
                firing.windowStartDate(),
                firing.windowEndDate());
    }
}
