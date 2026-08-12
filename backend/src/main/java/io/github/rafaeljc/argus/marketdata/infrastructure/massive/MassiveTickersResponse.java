package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveTickersResponse(@JsonProperty("results") List<MassiveTicker> results,
                                     @JsonProperty("next_url") String nextUrl) {

    public MassiveTickersResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
