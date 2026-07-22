package io.github.rafaeljc.argus.transactions.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EditTransactionRequest(
        Operation operation,
        @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @JsonProperty("trade_date") LocalDate tradeDate) {}
