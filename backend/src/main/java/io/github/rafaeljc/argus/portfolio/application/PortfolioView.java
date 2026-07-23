package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Money;
import java.time.LocalDate;
import java.util.List;

public record PortfolioView(LocalDate asOfDate, Money totalValue, boolean totalValuePending, List<Position> positions) {

    public PortfolioView {
        if (asOfDate == null) {
            throw new IllegalArgumentException("PortfolioView asOfDate must not be null");
        }
        if (totalValue == null) {
            throw new IllegalArgumentException("PortfolioView totalValue must not be null");
        }
        if (positions == null) {
            throw new IllegalArgumentException("PortfolioView positions must not be null");
        }
        positions = List.copyOf(positions);
    }
}
