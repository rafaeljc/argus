package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MassiveAggregatesResponse(@JsonProperty("results") List<MassiveAggregate> results) {

    // The vendor omits "results" entirely when a window has no bars rather than sending [].
    public MassiveAggregatesResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
