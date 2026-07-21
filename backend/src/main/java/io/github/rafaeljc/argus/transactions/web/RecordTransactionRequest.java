package io.github.rafaeljc.argus.transactions.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordTransactionRequest(
        @NotBlank @Pattern(regexp = "^[A-Z.]{1,10}$") String ticker,
        @NotNull Operation operation,
        @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @JsonProperty("trade_date") @NotNull LocalDate tradeDate) {}
