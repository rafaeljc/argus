package io.github.rafaeljc.argus.alerts.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAlertRuleRequest(
        @NotNull Direction direction,
        @NotNull @DecimalMin("0.5") @DecimalMax("100") @Digits(integer = 3, fraction = 1) BigDecimal threshold,
        @JsonProperty("window_days") @NotNull @LookbackWindowDays Integer windowDays) {}
