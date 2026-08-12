package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Vendor JSON -> domain. Kept separate from the gateway so every mapping rule below is covered by
// plain unit tests, with no HTTP in the way.
//
// The vendor returns the whole US market, which includes instruments Argus does not model:
// warrants, units and preferred classes whose symbols fall outside Ticker's pattern, venues
// outside NYSE/NASDAQ, and halted names quoting a zero close. Each is skipped rather than thrown
// on — one unusable row must never abort a universe sweep or a day's closes.
public class MassiveResponseMapper {

    // Daily bars are stamped at the start of the exchange-local trading day. Converting in UTC
    // would date bars a day late for any exchange offset that crosses midnight.
    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");

    private static final Map<String, Exchange> EXCHANGE_BY_MIC =
            Map.of("XNYS", Exchange.NYSE, "XNAS", Exchange.NASDAQ);

    public List<PriceHistory> toClosesForTicker(List<MassiveAggregate> aggregates, Ticker ticker, Instant now) {
        List<PriceHistory> closes = new ArrayList<>();
        for (MassiveAggregate aggregate : aggregates) {
            addClose(closes, ticker, aggregate, now);
        }
        return List.copyOf(closes);
    }

    public List<PriceHistory> toClosesForTickers(
            List<MassiveAggregate> aggregates, Set<Ticker> requested, Instant now) {
        List<PriceHistory> closes = new ArrayList<>();
        for (MassiveAggregate aggregate : aggregates) {
            Ticker ticker = parseTicker(aggregate.ticker());
            if (ticker != null && requested.contains(ticker)) {
                addClose(closes, ticker, aggregate, now);
            }
        }
        return List.copyOf(closes);
    }

    public Set<Symbol> toSymbols(List<MassiveTicker> vendorTickers, Instant now) {
        Set<Symbol> symbols = new LinkedHashSet<>();
        for (MassiveTicker vendorTicker : vendorTickers) {
            Ticker ticker = parseTicker(vendorTicker.ticker());
            Exchange exchange = exchangeOf(vendorTicker.primaryExchange());
            if (ticker != null && exchange != null) {
                symbols.add(new Symbol(ticker, exchange, vendorTicker.name(), false, now, now, now));
            }
        }
        return Set.copyOf(symbols);
    }

    private void addClose(List<PriceHistory> closes, Ticker ticker, MassiveAggregate aggregate, Instant now) {
        if (aggregate.close() == null || aggregate.close().signum() <= 0) {
            return;
        }
        LocalDate tradeDate =
                Instant.ofEpochMilli(aggregate.timestampMillis()).atZone(EXCHANGE_ZONE).toLocalDate();
        closes.add(new PriceHistory(ticker, tradeDate, aggregate.close(), true, now, now));
    }

    private Exchange exchangeOf(String mic) {
        return mic == null ? null : EXCHANGE_BY_MIC.get(mic);
    }

    private Ticker parseTicker(String value) {
        try {
            return new Ticker(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
