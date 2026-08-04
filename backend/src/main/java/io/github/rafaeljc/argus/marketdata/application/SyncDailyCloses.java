package io.github.rafaeljc.argus.marketdata.application;

import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SyncDailyCloses {

    private final VendorPriceGateway gateway;
    private final PriceHistoryRepository priceHistory;
    private final CircuitBreaker breaker;

    public SyncDailyCloses(
            VendorPriceGateway gateway, PriceHistoryRepository priceHistory, CircuitBreaker vendorMarketdataBreaker) {
        this.gateway = gateway;
        this.priceHistory = priceHistory;
        this.breaker = vendorMarketdataBreaker;
    }

    public int sync(Set<Ticker> tickers, LocalDate tradeDate) {
        if (tickers.isEmpty()) {
            return 0;
        }
        List<PriceHistory> closes = breaker.executeSupplier(() -> gateway.fetchClosesOn(tickers, tradeDate));
        if (closes.isEmpty()) {
            return 0;
        }
        return priceHistory.upsertBatch(closes);
    }
}
