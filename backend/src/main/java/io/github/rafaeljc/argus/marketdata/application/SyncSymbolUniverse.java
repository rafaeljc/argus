package io.github.rafaeljc.argus.marketdata.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SyncSymbolUniverse {

    private final VendorPriceGateway gateway;
    private final SymbolRepository symbols;
    private final CircuitBreaker breaker;
    private final Clock clock;

    public SyncSymbolUniverse(
            VendorPriceGateway gateway,
            SymbolRepository symbols,
            CircuitBreaker vendorMarketdataBreaker,
            Clock clock) {
        this.gateway = gateway;
        this.symbols = symbols;
        this.breaker = vendorMarketdataBreaker;
        this.clock = clock;
    }

    public SymbolSyncResult sync() {
        Instant asOf = clock.now();
        Set<Symbol> universe = breaker.executeSupplier(gateway::fetchSymbolUniverse);
        // An empty universe means "nothing to reconcile" (no-op adapter, or a degenerate vendor
        // response) — never treat it as "the vendor confirms zero tickers exist" and wipe out
        // every locally tracked symbol.
        if (universe.isEmpty()) {
            return new SymbolSyncResult(0, 0, 0);
        }
        int upserted = symbols.upsertAll(universe, asOf);
        int delisted = symbols.markDelistedIfNotSyncedAt(asOf);
        return new SymbolSyncResult(upserted, delisted, universe.size());
    }
}
