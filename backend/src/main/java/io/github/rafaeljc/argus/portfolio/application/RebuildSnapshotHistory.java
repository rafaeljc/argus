package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.PriceLookup;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings;
import io.github.rafaeljc.argus.portfolio.application.port.LedgerHoldings.NetQuantityPoint;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RebuildSnapshotHistory {

    private final LedgerHoldings ledgerHoldings;
    private final PriceLookup priceLookup;
    private final PortfolioSnapshotRepository repository;
    private final Clock clock;

    public RebuildSnapshotHistory(
            LedgerHoldings ledgerHoldings,
            PriceLookup priceLookup,
            PortfolioSnapshotRepository repository,
            Clock clock) {
        this.ledgerHoldings = ledgerHoldings;
        this.priceLookup = priceLookup;
        this.repository = repository;
        this.clock = clock;
    }

    public void rebuild(UserId userId) {
        LocalDate today = clock.today();
        List<NetQuantityPoint> timeline = ledgerHoldings.timeline(userId, today);
        List<PortfolioSnapshot> rows = timeline.isEmpty() ? List.of() : valueTimeline(userId, timeline, today);

        repository.deleteByUser(userId);
        if (!rows.isEmpty()) {
            repository.insertAll(rows);
        }
    }

    private List<PortfolioSnapshot> valueTimeline(UserId userId, List<NetQuantityPoint> timeline, LocalDate today) {
        LocalDate from = timeline.stream().map(NetQuantityPoint::effectiveFrom).min(LocalDate::compareTo).get();
        Set<Ticker> tickers = timeline.stream().map(NetQuantityPoint::ticker).collect(Collectors.toSet());
        Map<LocalDate, Map<Ticker, BigDecimal>> closesByDate = priceLookup.closesBetween(tickers, from, today);

        List<PortfolioSnapshot> rows = new ArrayList<>();
        Map<Ticker, BigDecimal> held = new HashMap<>();
        int cursor = 0;
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            while (cursor < timeline.size() && !timeline.get(cursor).effectiveFrom().isAfter(day)) {
                applyPoint(held, timeline.get(cursor));
                cursor++;
            }
            Map<Ticker, BigDecimal> closesOnDay = closesByDate.getOrDefault(day, Map.of());
            LocalDate rowDate = day;
            SnapshotValuation.valueFor(held, closesOnDay)
                    .ifPresent(total -> rows.add(new PortfolioSnapshot(userId, rowDate, total)));
        }
        return rows;
    }

    private static void applyPoint(Map<Ticker, BigDecimal> held, NetQuantityPoint point) {
        if (point.netQuantity().signum() > 0) {
            held.put(point.ticker(), point.netQuantity());
        } else {
            held.remove(point.ticker());
        }
    }
}
