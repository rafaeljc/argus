package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveTicker(@JsonProperty("ticker") String ticker,
                            @JsonProperty("name") String name,
                            @JsonProperty("primary_exchange") String primaryExchange) {
}
