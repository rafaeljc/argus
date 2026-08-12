package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ServiceUnavailableException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class MassivePriceGatewayTest {

    private static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");
    private static final String BASE_URL = "https://api.massive.example";
    private static final String API_KEY = "test-api-key";

    private static final long MARCH_10_BAR = Instant.parse("2026-03-10T04:00:00Z").toEpochMilli();

    private static final String TICKERS_URL =
            BASE_URL + "/v3/reference/tickers?market=stocks&active=true&limit=1000";
    private static final String HISTORY_URL =
            BASE_URL + "/v2/aggs/ticker/AAPL/range/1/day/2026-03-09/2026-03-10?adjusted=true";
    private static final String GROUPED_URL =
            BASE_URL + "/v2/aggs/grouped/locale/us/market/stocks/2026-03-10?adjusted=true";

    private MockRestServiceServer server;
    private MassivePriceGateway gateway;

    @BeforeEach
    void setUp() {
        buildGateway(3);
    }

    private void buildGateway(int maxUniversePages) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MassiveProperties properties = new MassiveProperties(
                API_KEY, BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(30), maxUniversePages);
        // Mirrors the `vendor-marketdata` instance in application.yaml, minus the multi-second
        // backoff. 429 is deliberately absent: MarketdataVendorWiringIT pins the real config, where
        // rate limiting is routed to the separate throttle retry below instead.
        Retry retry = Retry.of(
                "test",
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(
                                HttpServerErrorException.class, ResourceAccessException.class, IOException.class)
                        .build());
        // Mirrors `vendor-marketdata-throttle` (MarketdataInfrastructureConfig), minus the real
        // wait. RetryAfterIntervalFunctionTest covers the Retry-After parsing in isolation;
        // MarketdataVendorWiringIT pins the production interval function and attempt count.
        Retry throttleRetry = Retry.of(
                "test-throttle",
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(HttpClientErrorException.TooManyRequests.class)
                        .build());
        gateway = new MassivePriceGateway(
                builder, properties, new MassiveResponseMapper(), retry, throttleRetry, new FixedClock(NOW));
    }

    private static String aggregatesBody(String results) {
        return "{\"status\":\"OK\",\"results\":[" + results + "]}";
    }

    @Test
    void fetchPriceHistory_vendorReturnsBars_mapsToSplitAdjustedCloses() {
        server.expect(requestTo(HISTORY_URL))
                .andRespond(withSuccess(
                        aggregatesBody("{\"c\":123.45,\"t\":" + MARCH_10_BAR + "}"), MediaType.APPLICATION_JSON));

        List<PriceHistory> result =
                gateway.fetchPriceHistory(new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10));

        assertThat(result).containsExactly(new PriceHistory(
                new Ticker("AAPL"), LocalDate.of(2026, 3, 10), new BigDecimal("123.45"), true, NOW, NOW));
        server.verify();
    }

    @Test
    void fetchPriceHistory_apiKey_isSentAsBearerHeaderNotQueryParam() {
        server.expect(requestTo(HISTORY_URL))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(aggregatesBody(""), MediaType.APPLICATION_JSON));

        gateway.fetchPriceHistory(new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10));

        server.verify();
    }

    @Test
    void fetchPriceHistory_nullResults_returnsEmptyList() {
        server.expect(requestTo(HISTORY_URL))
                .andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));

        List<PriceHistory> result =
                gateway.fetchPriceHistory(new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10));

        assertThat(result).isEmpty();
    }

    @Test
    void fetchPriceHistory_serverError_retriesThreeTimesThenThrowsServiceUnavailable() {
        server.expect(ExpectedCount.times(3), requestTo(HISTORY_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> gateway.fetchPriceHistory(
                        new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10)))
                .isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }

    // 429 is throttling, not failure: the account-wide free-tier ceiling is shared across every
    // vendor call, so it must be retried on its own dedicated schedule, separate from the transient-
    // error retry above. See RetryAfterIntervalFunction for the actual wait-duration logic.
    @Test
    void fetchPriceHistory_rateLimited_isRetriedViaThrottleRetryUntilSuccess() {
        server.expect(ExpectedCount.times(2), requestTo(HISTORY_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(HISTORY_URL))
                .andRespond(withSuccess(
                        aggregatesBody("{\"c\":123.45,\"t\":" + MARCH_10_BAR + "}"), MediaType.APPLICATION_JSON));

        List<PriceHistory> result =
                gateway.fetchPriceHistory(new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10));

        assertThat(result).hasSize(1);
        server.verify();
    }

    // A safety valve, not the expected case: if the account stays throttled far longer than a
    // normal window (revoked key, plan downgrade), the call must eventually give up rather than
    // block a background worker thread forever.
    @Test
    void fetchPriceHistory_persistentlyRateLimited_throwsServiceUnavailableAfterThrottleBudgetExhausted() {
        server.expect(ExpectedCount.times(3), requestTo(HISTORY_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> gateway.fetchPriceHistory(
                        new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10)))
                .isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }

    @Test
    void fetchPriceHistory_connectionFailure_isRetried() {
        server.expect(ExpectedCount.times(2), requestTo(HISTORY_URL))
                .andRespond(withException(new IOException("connection reset")));
        server.expect(requestTo(HISTORY_URL))
                .andRespond(withSuccess(
                        aggregatesBody("{\"c\":123.45,\"t\":" + MARCH_10_BAR + "}"), MediaType.APPLICATION_JSON));

        List<PriceHistory> result =
                gateway.fetchPriceHistory(new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10));

        assertThat(result).hasSize(1);
        server.verify();
    }

    // A rejected key is not transient: burning the retry budget on it only delays the failure and
    // eats the vendor's rate limit.
    @Test
    void fetchPriceHistory_unauthorized_failsFastWithoutRetrying() {
        server.expect(ExpectedCount.once(), requestTo(HISTORY_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.fetchPriceHistory(
                        new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10)))
                .isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }

    // "The vendor has no bars for this window" is a normal backfill outcome, not a failure.
    @Test
    void fetchPriceHistory_notFound_returnsEmptyListWithoutRetrying() {
        server.expect(ExpectedCount.once(), requestTo(HISTORY_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<PriceHistory> result =
                gateway.fetchPriceHistory(new Ticker("AAPL"), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10));

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void fetchClosesOn_manyTickers_usesOneGroupedCallAndFiltersToRequested() {
        server.expect(ExpectedCount.once(), requestTo(GROUPED_URL))
                .andRespond(withSuccess(
                        aggregatesBody("{\"T\":\"AAPL\",\"c\":123.45,\"t\":" + MARCH_10_BAR + "},"
                                + "{\"T\":\"MSFT\",\"c\":400.00,\"t\":" + MARCH_10_BAR + "}"),
                        MediaType.APPLICATION_JSON));

        List<PriceHistory> result = gateway.fetchClosesOn(Set.of(new Ticker("AAPL")), LocalDate.of(2026, 3, 10));

        assertThat(result).extracting(PriceHistory::ticker).containsExactly(new Ticker("AAPL"));
        server.verify();
    }

    @Test
    void fetchSymbolUniverse_singlePage_mapsSupportedExchanges() {
        server.expect(requestTo(TICKERS_URL))
                .andRespond(withSuccess(
                        "{\"results\":[{\"ticker\":\"AAPL\",\"name\":\"Apple Inc.\",\"primary_exchange\":\"XNAS\"}]}",
                        MediaType.APPLICATION_JSON));

        Set<Symbol> result = gateway.fetchSymbolUniverse();

        assertThat(result)
                .containsExactly(new Symbol(
                        new Ticker("AAPL"), Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
        server.verify();
    }

    @Test
    void fetchSymbolUniverse_cursoredResponse_followsNextUrl() {
        String nextUrl = BASE_URL + "/v3/reference/tickers?cursor=page2";
        server.expect(requestTo(TICKERS_URL))
                .andRespond(withSuccess(
                        "{\"results\":[{\"ticker\":\"AAPL\",\"name\":\"Apple\",\"primary_exchange\":\"XNAS\"}],"
                                + "\"next_url\":\"" + nextUrl + "\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(nextUrl))
                .andRespond(withSuccess(
                        "{\"results\":[{\"ticker\":\"KO\",\"name\":\"Coca-Cola\",\"primary_exchange\":\"XNYS\"}]}",
                        MediaType.APPLICATION_JSON));

        Set<Symbol> result = gateway.fetchSymbolUniverse();

        assertThat(result).extracting(Symbol::ticker).containsExactlyInAnyOrder(new Ticker("AAPL"), new Ticker("KO"));
        server.verify();
    }

    // Pagination is bounded so a runaway cursor cannot spin the sweep against the vendor forever.
    @Test
    void fetchSymbolUniverse_moreCursorsThanPageBudget_stopsAtBudget() {
        buildGateway(1);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(
                        "{\"results\":[{\"ticker\":\"AAPL\",\"name\":\"Apple\",\"primary_exchange\":\"XNAS\"}],"
                                + "\"next_url\":\"" + BASE_URL + "/v3/reference/tickers?cursor=page2\"}",
                        MediaType.APPLICATION_JSON));

        Set<Symbol> result = gateway.fetchSymbolUniverse();

        assertThat(result).extracting(Symbol::ticker).containsExactly(new Ticker("AAPL"));
        server.verify();
    }

    // SyncSymbolUniverse treats an empty universe as "nothing to reconcile"; throwing here would
    // turn a vendor hiccup into a mass delisting.
    @Test
    void fetchSymbolUniverse_serverError_throwsServiceUnavailableRatherThanReturningEmpty() {
        server.expect(ExpectedCount.times(3), requestTo(TICKERS_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> gateway.fetchSymbolUniverse()).isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }
}
