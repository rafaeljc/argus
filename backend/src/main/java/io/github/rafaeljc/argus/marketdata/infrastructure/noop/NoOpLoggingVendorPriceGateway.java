package io.github.rafaeljc.argus.marketdata.infrastructure.noop;

import io.github.rafaeljc.argus.common.domain.ServiceUnavailableException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Profile-gated to local + test so a missing production adapter fails wiring instead of
// silently swallowing vendor calls. Symbol-universe fetches return empty (safe no-op — the
// upstream sweep just has nothing to reconcile). Price-history fetches throw because the
// backfill worker treats an empty result as "vendor says no data for this window", which
// would mask the fact that no vendor is wired at all.
@Component
@Profile({"local", "test"})
public class NoOpLoggingVendorPriceGateway implements VendorPriceGateway {

    private static final Logger log = LoggerFactory.getLogger(NoOpLoggingVendorPriceGateway.class);

    @Override
    public Set<Symbol> fetchSymbolUniverse() {
        log.info("vendor marketdata no-op fetchSymbolUniverse: returning empty set");
        return Set.of();
    }

    @Override
    public List<PriceHistory> fetchPriceHistory(Ticker ticker, LocalDate start, LocalDate end) {
        log.info(
                "vendor marketdata no-op fetchPriceHistory: ticker={} start={} end={}",
                ticker.value(),
                start,
                end);
        throw new ServiceUnavailableException("vendor marketdata unavailable (no-op adapter)");
    }
}
