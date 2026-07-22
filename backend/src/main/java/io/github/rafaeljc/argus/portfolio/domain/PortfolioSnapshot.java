package io.github.rafaeljc.argus.portfolio.domain;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.LocalDate;

public record PortfolioSnapshot(UserId userId, LocalDate snapshotDate, Money totalValue) {

    public PortfolioSnapshot {
        if (userId == null) {
            throw new IllegalArgumentException("PortfolioSnapshot userId must not be null");
        }
        if (snapshotDate == null) {
            throw new IllegalArgumentException("PortfolioSnapshot snapshotDate must not be null");
        }
        if (totalValue == null) {
            throw new IllegalArgumentException("PortfolioSnapshot totalValue must not be null");
        }
    }
}
