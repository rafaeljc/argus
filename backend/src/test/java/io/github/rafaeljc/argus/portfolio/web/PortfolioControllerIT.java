package io.github.rafaeljc.argus.portfolio.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
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

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortfolioControllerIT {

    private static final String ENDPOINT = "/api/v1/portfolio";
    private static final String PASSWORD = "correct horse battery staple";
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
    private SessionRepository sessionRepository;

    @Autowired
    private SymbolRepository symbolRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Test
    void getPortfolio_pricedHoldings_returnsEnvelopeWithTotalAndPositions() throws Exception {
        seedSymbol(AAPL, false);
        seedSymbol(MSFT, false);
        priceHistoryRepository.upsertBatch(
                List.of(closeOn(AAPL, "150.00"), closeOn(MSFT, "420.75")));
        User user = seedVerified("alice@example.com");
        holdingRepository.upsert(user.id(), AAPL, new Quantity(new BigDecimal("10")), SYMBOL_NOW);
        holdingRepository.upsert(user.id(), MSFT, new Quantity(new BigDecimal("2")), SYMBOL_NOW);

        ResponseEntity<String> response = get(user);

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
        User user = seedVerified("bob@example.com");

        ResponseEntity<String> response = get(user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("total_value").asString()).isEqualTo("0.00");
        assertThat(data.get("total_value_pending").asBoolean()).isFalse();
        assertThat(data.get("positions")).isEmpty();
    }

    @Test
    void getPortfolio_heldTickerWithNoPriceHistory_returnsPricePendingPosition() throws Exception {
        User user = seedVerified("carol@example.com");
        seedSymbol(AAPL, false);
        holdingRepository.upsert(user.id(), AAPL, new Quantity(new BigDecimal("10")), SYMBOL_NOW);

        ResponseEntity<String> response = get(user);

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
        User user = seedVerified("dave@example.com");
        seedSymbol(GE, true);
        priceHistoryRepository.upsertBatch(List.of(closeOn(GE, "50.00")));
        holdingRepository.upsert(user.id(), GE, new Quantity(new BigDecimal("10")), SYMBOL_NOW);

        ResponseEntity<String> response = get(user);

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

    private void seedSymbol(Ticker ticker, boolean delisted) {
        symbolRepository.save(new Symbol(
                ticker, Exchange.NASDAQ, ticker.value() + " Inc.", delisted, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
    }

    private static PriceHistory closeOn(Ticker ticker, String close) {
        return new PriceHistory(ticker, CLOSE_DATE, new BigDecimal(close), true, SYMBOL_NOW, SYMBOL_NOW);
    }

    private User seedVerified(String email) {
        User u = userService.createUnverified(email, PASSWORD);
        return userService.markVerified(u.id());
    }

    private ResponseEntity<String> get(User authenticatedAs) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionToken);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String seedSession(User user) {
        String token = "portfolio-it-session-" + UuidCreator.getTimeOrderedEpoch();
        Instant now = Instant.now();
        sessionRepository.save(new Session(
                new SessionId(UuidCreator.getTimeOrderedEpoch()),
                user.id(),
                sha256Hex(token),
                "10.0.0.1",
                "IT-Agent",
                now,
                now.plus(Duration.ofDays(30)),
                now));
        return token;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
