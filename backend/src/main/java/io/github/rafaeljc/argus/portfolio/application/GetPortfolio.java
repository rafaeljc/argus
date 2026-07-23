package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolLookup;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GetPortfolio {

    private final GetActiveHoldings getActiveHoldings;
    private final PriceLookup priceLookup;
    private final SymbolLookup symbolLookup;
    private final Clock clock;

    public GetPortfolio(
            GetActiveHoldings getActiveHoldings, PriceLookup priceLookup, SymbolLookup symbolLookup, Clock clock) {
        this.getActiveHoldings = getActiveHoldings;
        this.priceLookup = priceLookup;
        this.symbolLookup = symbolLookup;
        this.clock = clock;
    }

    public PortfolioView forUser(UserId userId) {
        List<Holding> holdings = getActiveHoldings.forUser(userId);
        if (holdings.isEmpty()) {
            return new PortfolioView(clock.today(), new Money(BigDecimal.ZERO), false, List.of());
        }

        Set<Ticker> tickers = holdings.stream().map(Holding::ticker).collect(Collectors.toSet());
        Map<Ticker, PriceLookup.Close> latestCloses = priceLookup.latestCloses(tickers);
        Set<Ticker> delisted = symbolLookup.delistedAmong(tickers);

        LocalDate asOfDate = latestCloses.values().stream()
                .map(PriceLookup.Close::tradeDate)
                .max(Comparator.naturalOrder())
                .orElseGet(clock::today);

        List<PricedHolding> priced = holdings.stream()
                .sorted(Comparator.comparing(h -> h.ticker().value()))
                .map(holding -> price(holding, latestCloses.get(holding.ticker()), delisted, asOfDate))
                .toList();

        boolean totalValuePending = priced.stream().anyMatch(PricedHolding::pending);
        Money totalValue = MoneyMath.sum(priced.stream()
                .filter(p -> !p.pending())
                .map(PricedHolding::positionValue)
                .toList());

        List<Position> positions = priced.stream().map(p -> p.toPosition(totalValue)).toList();

        return new PortfolioView(asOfDate, totalValue, totalValuePending, positions);
    }

    private static PricedHolding price(
            Holding holding, PriceLookup.Close close, Set<Ticker> delisted, LocalDate asOfDate) {
        if (close == null) {
            return PricedHolding.pending(holding);
        }
        boolean stale = delisted.contains(holding.ticker()) || close.tradeDate().isBefore(asOfDate);
        Money value = MoneyMath.multiplyHalfEven(close.price(), holding.quantity());
        return PricedHolding.priced(holding, close, value, stale);
    }

    private record PricedHolding(
            Holding holding, PriceLookup.Close close, Money positionValue, boolean pending, boolean stale) {

        static PricedHolding pending(Holding holding) {
            return new PricedHolding(holding, null, null, true, false);
        }

        static PricedHolding priced(Holding holding, PriceLookup.Close close, Money value, boolean stale) {
            return new PricedHolding(holding, close, value, false, stale);
        }

        Position toPosition(Money totalValue) {
            if (pending) {
                return new Position(
                        holding.ticker(), holding.quantity(), null, null, null, null, true, false, null);
            }
            BigDecimal percent = MoneyMath.percentOf(positionValue, totalValue).orElse(null);
            LocalDate staleSince = stale ? close.tradeDate() : null;
            return new Position(
                    holding.ticker(),
                    holding.quantity(),
                    close.price(),
                    close.tradeDate(),
                    positionValue,
                    percent,
                    false,
                    stale,
                    staleSince);
        }
    }
}
