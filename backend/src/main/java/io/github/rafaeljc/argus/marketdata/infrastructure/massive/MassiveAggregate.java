package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

// One daily OHLC bar. The single-ticker aggregates endpoint omits "T" (the ticker is in the
// request path); the grouped endpoint carries it per row.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveAggregate(@JsonProperty("T") String ticker,
                               @JsonProperty("c") BigDecimal close,
                               @JsonProperty("t") long timestampMillis) {
}
