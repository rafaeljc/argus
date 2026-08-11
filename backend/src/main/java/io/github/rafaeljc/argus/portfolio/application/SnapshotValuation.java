package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SnapshotValuation {

    private SnapshotValuation() {}

    static Optional<Money> valueFor(Map<Ticker, BigDecimal> heldQuantities, Map<Ticker, BigDecimal> closesOnDate) {
        if (heldQuantities.isEmpty()) {
            return Optional.of(new Money(BigDecimal.ZERO));
        }
        boolean hasFullCoverage = heldQuantities.keySet().stream().allMatch(closesOnDate::containsKey);
        if (!hasFullCoverage) {
            return Optional.empty();
        }
        List<Money> positionValues = heldQuantities.entrySet().stream()
                .map(entry -> MoneyMath.multiplyHalfEven(
                        closesOnDate.get(entry.getKey()), new Quantity(entry.getValue())))
                .toList();
        return Optional.of(MoneyMath.sum(positionValues));
    }
}
