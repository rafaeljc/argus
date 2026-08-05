package io.github.rafaeljc.argus.eodpipeline.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record TriggerRunRequest(@JsonProperty("run_date") @PastOrPresent LocalDate runDate) {
}
