package io.github.rafaeljc.argus.common.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CollectionEnvelope<T>(List<T> data, Meta meta, Links links) {

    public CollectionEnvelope {
        if (data == null) {
            throw new IllegalArgumentException("CollectionEnvelope data must not be null");
        }
        if (meta == null) {
            throw new IllegalArgumentException("CollectionEnvelope meta must not be null");
        }
        if (links == null) {
            throw new IllegalArgumentException("CollectionEnvelope links must not be null");
        }
        data = List.copyOf(data);
    }

    public record Meta(
            int total,
            int page,
            @JsonProperty("per_page") int perPage,
            @JsonProperty("total_pages") int totalPages) {
    }

    public record Links(String self, String next, String prev, String last) {

        public Links {
            if (self == null) {
                throw new IllegalArgumentException("Links self must not be null");
            }
        }
    }
}
