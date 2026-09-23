package io.github.rafaeljc.argus.portfolio.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortfolioControllerIT {

    private static final String ENDPOINT = "/api/v1/portfolio";
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GE = new Ticker("GE");
    private static final Instant SYMBOL_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate CLOSE_DATE = LocalDate.parse("2026-06-10");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SymbolRepository symbolRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private Clock clock;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void getPortfolio_pricedHoldings_returnsEnvelopeWithTotalAndPositions() throws Exception {
        seedSymbol(AAPL, false);
        seedSymbol(MSFT, false);
        priceHistoryRepository.upsertBatch(
                List.of(closeOn(AAPL, "150.00"), closeOn(MSFT, "420.75")));
        TestSession session = testLogin.login("alice@example.com");
        holdingRepository.upsert(session.userId(), AAPL, new Quantity(new BigDecimal("10")), SYMBOL_NOW);
        holdingRepository.upsert(session.userId(), MSFT, new Quantity(new BigDecimal("2")), SYMBOL_NOW);

        ResponseEntity<String> response = get(session);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("as_of_date").asString()).isEqualTo(CLOSE_DATE.toString());
        assertThat(data.get("total_value").asString()).isEqualTo("2341.50");
        assertThat(data.get("total_value_pending").asBoolean()).isFalse();
        JsonNode positions = data.get("positions");
        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).get("ticker").asString()).isEqualTo("AAPL");
        assertThat(positions.get(0).get("position_value").asString()).isEqualTo("1500.00");
        assertThat(positions.get(1).get("ticker").asString()).isEqualTo("MSFT");
    }

    @Test
    void getPortfolio_noHoldings_returnsZeroTotalAndEmptyPositions() throws Exception {
        TestSession session = testLogin.login("bob@example.com");

        ResponseEntity<String> response = get(session);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("total_value").asString()).isEqualTo("0.00");
        assertThat(data.get("total_value_pending").asBoolean()).isFalse();
        assertThat(data.get("positions")).isEmpty();
    }

    @Test
    void getPortfolio_heldTickerWithNoPriceHistory_returnsPricePendingPosition() throws Exception {
        TestSession session = testLogin.login("carol@example.com");
        seedSymbol(AAPL, false);
        holdingRepository.upsert(session.userId(), AAPL, new Quantity(new BigDecimal("10")), SYMBOL_NOW);

        ResponseEntity<String> response = get(session);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("total_value_pending").asBoolean()).isTrue();
        JsonNode position = data.get("positions").get(0);
        assertThat(position.get("price_pending").asBoolean()).isTrue();
        assertThat(position.get("last_close_price").isNull()).isTrue();
        assertThat(position.get("position_value").isNull()).isTrue();
    }

    @Test
    void getPortfolio_delistedHolding_returnsPriceStalePosition() throws Exception {
        TestSession session = testLogin.login("dave@example.com");
        seedSymbol(GE, true);
        priceHistoryRepository.upsertBatch(List.of(closeOn(GE, "50.00")));
        holdingRepository.upsert(session.userId(), GE, new Quantity(new BigDecimal("10")), SYMBOL_NOW);

        ResponseEntity<String> response = get(session);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode position = json.readTree(response.getBody()).get("data").get("positions").get(0);
        assertThat(position.get("price_stale").asBoolean()).isTrue();
        assertThat(position.get("stale_since").asString()).isEqualTo(CLOSE_DATE.toString());
        assertThat(position.get("position_value").asString()).isEqualTo("500.00");
    }

    @Test
    void getPortfolio_unauthenticated_returns401() {
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void listSnapshots_defaultRange_excludesSnapshotOlderThanOneYear() throws Exception {
        TestSession session = testLogin.login("erin@example.com");
        LocalDate anchor = clock.today().minusDays(1);
        seedSnapshot(session, anchor, "100.00");
        seedSnapshot(session, anchor.minusYears(1).minusDays(1), "80.00");

        ResponseEntity<String> response = getSnapshots(session, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("snapshot_date").asString()).isEqualTo(anchor.toString());
    }

    @Test
    void listSnapshots_multipleSnapshotsInRange_returnsAscendingByDate() throws Exception {
        TestSession session = testLogin.login("frank@example.com");
        LocalDate anchor = clock.today().minusDays(1);
        seedSnapshot(session, anchor, "100.00");
        seedSnapshot(session, anchor.minusMonths(6), "90.00");

        ResponseEntity<String> response = getSnapshots(session, "1y");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get(0).get("snapshot_date").asString()).isEqualTo(anchor.minusMonths(6).toString());
        assertThat(data.get(0).get("total_value").asString()).isEqualTo("90.00");
        assertThat(data.get(1).get("snapshot_date").asString()).isEqualTo(anchor.toString());
    }

    @Test
    void listSnapshots_snapshotDatedToday_excludedFromResult() throws Exception {
        TestSession session = testLogin.login("grace@example.com");
        seedSnapshot(session, clock.today(), "999.00");

        ResponseEntity<String> response = getSnapshots(session, "5y");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).get("data")).isEmpty();
    }

    @Test
    void listSnapshots_unknownRange_returns422ValidationError() {
        TestSession session = testLogin.login("heidi@example.com");

        ResponseEntity<String> response = getSnapshots(session, "2y");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void listSnapshots_noSnapshots_returnsEmptyData() throws Exception {
        TestSession session = testLogin.login("ivan@example.com");

        ResponseEntity<String> response = getSnapshots(session, "1y");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).get("data")).isEmpty();
    }

    @Test
    void listSnapshots_unauthenticated_returns401() {
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT + "/snapshots", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private void seedSnapshot(TestSession session, LocalDate date, String totalValue) {
        snapshotRepository.insertIfAbsent(
                new PortfolioSnapshot(session.userId(), date, new Money(new BigDecimal(totalValue))));
    }

    private ResponseEntity<String> getSnapshots(TestSession session, String range) {
        String url = "http://localhost:" + port + ENDPOINT + "/snapshots" + (range == null ? "" : "?range=" + range);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(session.headers()), String.class);
    }

    private void seedSymbol(Ticker ticker, boolean delisted) {
        symbolRepository.save(new Symbol(
                ticker, Exchange.NASDAQ, ticker.value() + " Inc.", delisted, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
    }

    private static PriceHistory closeOn(Ticker ticker, String close) {
        return new PriceHistory(ticker, CLOSE_DATE, new BigDecimal(close), true, SYMBOL_NOW, SYMBOL_NOW);
    }

    private ResponseEntity<String> get(TestSession session) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT, HttpMethod.GET, new HttpEntity<>(session.headers()),
                String.class);
    }
}
