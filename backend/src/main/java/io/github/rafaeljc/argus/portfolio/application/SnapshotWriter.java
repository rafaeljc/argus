package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SnapshotWriter {

    private final GetActiveHoldings getActiveHoldings;
    private final PriceLookup priceLookup;
    private final PortfolioSnapshotRepository repository;

    public SnapshotWriter(
            GetActiveHoldings getActiveHoldings, PriceLookup priceLookup, PortfolioSnapshotRepository repository) {
        this.getActiveHoldings = getActiveHoldings;
        this.priceLookup = priceLookup;
        this.repository = repository;
    }

    public void writeFor(UserId userId, LocalDate snapshotDate) {
        List<Holding> holdings = getActiveHoldings.forUser(userId);
        if (holdings.isEmpty()) {
            repository.insertIfAbsent(new PortfolioSnapshot(userId, snapshotDate, new Money(BigDecimal.ZERO)));
            return;
        }

        Set<Ticker> tickers = holdings.stream().map(Holding::ticker).collect(Collectors.toSet());
        Map<Ticker, BigDecimal> closes = priceLookup.closesOn(tickers, snapshotDate);
        boolean hasFullCoverage = holdings.stream().allMatch(h -> closes.containsKey(h.ticker()));
        if (!hasFullCoverage) {
            return;
        }

        List<Money> positionValues = holdings.stream()
                .map(h -> MoneyMath.multiplyHalfEven(closes.get(h.ticker()), h.quantity()))
                .toList();
        Money total = MoneyMath.sum(positionValues);
        repository.insertIfAbsent(new PortfolioSnapshot(userId, snapshotDate, total));
    }
}
