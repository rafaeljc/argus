package io.github.rafaeljc.argus.portfolio.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.time.LocalDate;

public record SnapshotResponse(
        @JsonProperty("snapshot_date") LocalDate snapshotDate, @JsonProperty("total_value") String totalValue) {

    public static SnapshotResponse from(PortfolioSnapshot snapshot) {
        return new SnapshotResponse(snapshot.snapshotDate(), snapshot.totalValue().value().toPlainString());
    }
}
