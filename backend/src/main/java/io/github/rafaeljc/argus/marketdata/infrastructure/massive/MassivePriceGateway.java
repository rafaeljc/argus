package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.ServiceUnavailableException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.resilience4j.retry.Retry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Market-data adapter over the vendor's REST API.
//
// Retry sits *inside* this class and the circuit breaker sits outside it, in the callers
// (SyncSymbolUniverse, SyncDailyCloses, BackfillWorker). That ordering matters: transient errors
// are exhausted here, so the breaker's sliding window records one outcome per logical call. The
// other way round, every retry attempt would count as its own failure and trip the breaker after a
// third of the calls it should take.
//
// Two Retry instances are composed around every call, not one: the vendor rate-limits the whole
// account (not per endpoint), so a 429 is throttling, not failure. It waits out the real window on
// its own dedicated retry/backoff schedule (vendorMarketdataThrottleRetry) instead of eating the
// transient-error retry's budget (vendorMarketdataRetry) — a burst of throttling would otherwise
// trip the circuit breaker for no reason. The two retries' predicates are disjoint by construction
// (see application.yaml), so nesting order between them doesn't change behavior.
public class MassivePriceGateway implements VendorPriceGateway {

    private static final Logger log = LoggerFactory.getLogger(MassivePriceGateway.class);

    private static final String TICKERS_PATH = "/v3/reference/tickers?market=stocks&active=true&limit=1000";
    private static final String HISTORY_PATH = "/v2/aggs/ticker/{ticker}/range/1/day/{start}/{end}?adjusted=true";
    private static final String GROUPED_PATH = "/v2/aggs/grouped/locale/us/market/stocks/{date}?adjusted=true";

    private final RestClient client;
    private final MassiveResponseMapper mapper;
    private final Retry retry;
    private final Retry throttleRetry;
    private final Clock clock;
    private final int maxUniversePages;

    public MassivePriceGateway(
            RestClient.Builder builder,
            MassiveProperties properties,
            MassiveResponseMapper mapper,
            Retry vendorMarketdataRetry,
            Retry vendorMarketdataThrottleRetry,
            Clock clock) {
        // Bearer header rather than the vendor's apiKey query parameter: query strings land in
        // access logs on every hop between here and the vendor. Transport settings (timeouts) are
        // applied to the builder by the caller, so tests can substitute one.
        this.client = builder.baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
        this.mapper = mapper;
        this.retry = vendorMarketdataRetry;
        this.throttleRetry = vendorMarketdataThrottleRetry;
        this.clock = clock;
        this.maxUniversePages = properties.maxUniversePages();
    }

    @Override
    public Set<Symbol> fetchSymbolUniverse() {
        Instant now = clock.now();
        Set<Symbol> universe = new LinkedHashSet<>();
        String url = TICKERS_PATH;
        for (int page = 0; page < maxUniversePages && url != null; page++) {
            String pageUrl = url;
            MassiveTickersResponse response = call(
                    "symbol universe",
                    () -> client.get().uri(pageUrl).retrieve().body(MassiveTickersResponse.class));
            if (response == null) {
                break;
            }
            universe.addAll(mapper.toSymbols(response.results(), now));
            url = response.nextUrl();
        }
        if (url != null) {
            log.warn("symbol universe truncated at {} pages; vendor still offered a cursor", maxUniversePages);
        }
        return Set.copyOf(universe);
    }

    @Override
    public List<PriceHistory> fetchPriceHistory(Ticker ticker, LocalDate start, LocalDate end) {
        MassiveAggregatesResponse response = call(
                "price history",
                () -> client.get()
                        .uri(HISTORY_PATH, ticker.value(), start, end)
                        .retrieve()
                        .body(MassiveAggregatesResponse.class));
        if (response == null) {
            return List.of();
        }
        return mapper.toClosesForTicker(response.results(), ticker, clock.now());
    }

    @Override
    public List<PriceHistory> fetchClosesOn(Set<Ticker> tickers, LocalDate tradeDate) {
        if (tickers.isEmpty()) {
            return List.of();
        }
        // The grouped endpoint returns the whole US market for one session, so a day's closes cost
        // a single request no matter how many tickers are held.
        MassiveAggregatesResponse response = call(
                "daily closes",
                () -> client.get().uri(GROUPED_PATH, tradeDate).retrieve().body(MassiveAggregatesResponse.class));
        if (response == null) {
            return List.of();
        }
        return mapper.toClosesForTickers(response.results(), tickers, clock.now());
    }

    // Returns null when the vendor has nothing for the request; throws when the call genuinely
    // failed, so the caller's circuit breaker sees it.
    private <T> T call(String operation, Supplier<T> request) {
        Supplier<T> resilient = Retry.decorateSupplier(throttleRetry, Retry.decorateSupplier(retry, request));
        try {
            return resilient.get();
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("vendor marketdata {}: no data for request", operation);
            return null;
        } catch (RestClientException ex) {
            log.warn("vendor marketdata {} failed", operation, ex);
            throw new ServiceUnavailableException("vendor marketdata " + operation + " failed", ex);
        }
    }
}
