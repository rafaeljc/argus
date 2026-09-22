package io.github.rafaeljc.argus.transactions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.transactions.application.TransactionService;
import io.github.rafaeljc.argus.transactions.domain.Operation;
import io.github.rafaeljc.argus.transactions.domain.Transaction;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionControllerIT {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final String ENDPOINT = "/api/v1/transactions";
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Instant SYMBOL_NOW = Instant.parse("2026-01-01T00:00:00Z");

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
    private TransactionService transactionService;

    @Autowired
    private BackfillJobRepository backfillJobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
        symbolRepository.save(
                new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
    }

    @Test
    void postTransactions_validBuy_returns201WithLocationAndEnvelope() throws Exception {
        TestSession user = testLogin.login("alice@example.com");
        LocalDate tradeDate = today().minusDays(1);

        ResponseEntity<String> response = post(user, transactionBody("AAPL", "BUY", "10", tradeDate));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).matches(".*/api/v1/transactions/[0-9a-fA-F-]{36}$");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("ticker").asString()).isEqualTo("AAPL");
        assertThat(data.get("operation").asString()).isEqualTo("BUY");
        assertThat(data.get("quantity").asString()).isEqualTo("10.000000");
        assertThat(data.get("trade_date").asString()).isEqualTo(tradeDate.toString());
        assertThat(data.get("id").asString()).isNotBlank();
        assertThat(data.get("created_at").asString()).isNotBlank();
    }

    @Test
    void postTransactions_validSellWithPriorBuy_returns201() {
        TestSession user = testLogin.login("bob@example.com");
        LocalDate buyDate = today().minusDays(10);
        LocalDate sellDate = today().minusDays(1);
        transactionService.record(user.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("10")), buyDate);

        ResponseEntity<String> response = post(user, transactionBody("AAPL", "SELL", "4", sellDate));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void postTransactions_oversell_returns422InsufficientHoldings() throws Exception {
        TestSession user = testLogin.login("carol@example.com");

        ResponseEntity<String> response = post(user, transactionBody("AAPL", "SELL", "5", today().minusDays(1)));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("INSUFFICIENT_HOLDINGS");
    }

    @Test
    void postTransactions_futureDate_returns422TradeDateFuture() throws Exception {
        TestSession user = testLogin.login("dave@example.com");

        ResponseEntity<String> response = post(user, transactionBody("AAPL", "BUY", "10", today().plusDays(1)));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("TRADE_DATE_FUTURE");
    }

    @Test
    void postTransactions_unknownTicker_returns422TickerNotFound() throws Exception {
        TestSession user = testLogin.login("erin@example.com");

        ResponseEntity<String> response = post(user, transactionBody("ZZZZ", "BUY", "10", today().minusDays(1)));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("TICKER_NOT_FOUND");
    }

    @Test
    void postTransactions_delistedBuy_returns422TickerDelisted() throws Exception {
        TestSession user = testLogin.login("frank@example.com");
        symbolRepository.save(new Symbol(new Ticker("GE"), Exchange.NYSE, "GE (delisted)", true,
                SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));

        ResponseEntity<String> response = post(user, transactionBody("GE", "BUY", "10", today().minusDays(1)));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("TICKER_DELISTED");
    }

    @Test
    void postTransactions_missingQuantity_returns422ValidationErrorWithQuantityField() throws Exception {
        TestSession user = testLogin.login("gina@example.com");
        String body = "{\"ticker\":\"AAPL\",\"operation\":\"BUY\",\"trade_date\":\"" + today().minusDays(1) + "\"}";

        ResponseEntity<String> response = post(user, body);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("quantity");
    }

    @Test
    void postTransactions_invalidOperationString_returns400MalformedRequest() throws Exception {
        TestSession user = testLogin.login("heidi@example.com");
        String body = "{\"ticker\":\"AAPL\",\"operation\":\"HOLD\",\"quantity\":\"10\",\"trade_date\":\""
                + today().minusDays(1) + "\"}";

        ResponseEntity<String> response = post(user, body);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void postTransactions_newTicker_enqueuesBackfillExactlyOnce() {
        TestSession user = testLogin.login("ivan@example.com");
        Ticker newTicker = new Ticker("NEWCO");
        symbolRepository.save(
                new Symbol(newTicker, Exchange.NASDAQ, "New Co", false, SYMBOL_NOW, SYMBOL_NOW, SYMBOL_NOW));
        LocalDate tradeDate = today().minusDays(1);

        ResponseEntity<String> first = post(user, transactionBody("NEWCO", "BUY", "5", tradeDate));
        ResponseEntity<String> second = post(user, transactionBody("NEWCO", "BUY", "5", tradeDate));

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(backfillJobRepository.findActiveByTicker(newTicker)).isPresent();
        Integer jobCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM backfill_jobs WHERE ticker = ?", Integer.class, newTicker.value());
        assertThat(jobCount).isEqualTo(1);
    }

    @Test
    void getTransactions_authenticated_returnsOwnedPageWithEnvelope() throws Exception {
        TestSession user = testLogin.login("judy@example.com");
        transactionService.record(user.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("10")),
                today().minusDays(2));
        transactionService.record(user.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("5")),
                today().minusDays(1));

        ResponseEntity<String> response = get(user, "");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).hasSize(2);
        JsonNode meta = body.get("meta");
        assertThat(meta.get("total").asInt()).isEqualTo(2);
        assertThat(meta.get("page").asInt()).isEqualTo(1);
        assertThat(meta.get("per_page").asInt()).isEqualTo(50);
        assertThat(meta.get("total_pages").asInt()).isEqualTo(1);
        JsonNode links = body.get("links");
        assertThat(links.get("self").asString()).contains("page=1").contains("per_page=50");
        assertThat(links.get("last").asString()).contains("page=1");
        assertThat(links.get("next").isNull()).isTrue();
        assertThat(links.get("prev").isNull()).isTrue();
    }

    @Test
    void getTransactions_empty_returnsEmptyDataWithZeroMeta() throws Exception {
        TestSession user = testLogin.login("kevin@example.com");

        ResponseEntity<String> response = get(user, "");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).isEmpty();
        assertThat(body.get("meta").get("total").asInt()).isZero();
        assertThat(body.get("meta").get("total_pages").asInt()).isZero();
        assertThat(body.get("links").get("next").isNull()).isTrue();
        assertThat(body.get("links").get("prev").isNull()).isTrue();
    }

    @Test
    void getTransactions_perPageOne_secondPage_setsNextPrevLast() throws Exception {
        TestSession user = testLogin.login("laura@example.com");
        transactionService.record(user.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("1")),
                today().minusDays(3));
        transactionService.record(user.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("1")),
                today().minusDays(2));
        transactionService.record(user.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("1")),
                today().minusDays(1));

        ResponseEntity<String> response = get(user, "?page=2&per_page=1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).hasSize(1);
        JsonNode meta = body.get("meta");
        assertThat(meta.get("total").asInt()).isEqualTo(3);
        assertThat(meta.get("page").asInt()).isEqualTo(2);
        assertThat(meta.get("total_pages").asInt()).isEqualTo(3);
        JsonNode links = body.get("links");
        assertThat(links.get("next").asString()).contains("page=3");
        assertThat(links.get("prev").asString()).contains("page=1");
        assertThat(links.get("last").asString()).contains("page=3");
    }

    @Test
    void getTransactions_onlyReturnsCallersTransactions() throws Exception {
        TestSession owner = testLogin.login("mike@example.com");
        TestSession other = testLogin.login("nina@example.com");
        transactionService.record(owner.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("10")),
                today().minusDays(1));
        transactionService.record(other.userId(), AAPL, Operation.BUY, new Quantity(new BigDecimal("20")),
                today().minusDays(1));

        ResponseEntity<String> response = get(owner, "");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("quantity").asString()).isEqualTo("10.000000");
    }

    @Test
    void getTransactions_perPageAboveMax_returns422() throws Exception {
        TestSession user = testLogin.login("oscar@example.com");

        ResponseEntity<String> response = get(user, "?per_page=201");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("per_page");
    }

    @Test
    void getTransactions_pageAboveMax_returns422() throws Exception {
        TestSession user = testLogin.login("oliver@example.com");

        ResponseEntity<String> response = get(user, "?page=100001");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("page");
    }

    @Test
    void getTransactions_perPageNonNumeric_returns400() throws Exception {
        TestSession user = testLogin.login("peggy@example.com");

        ResponseEntity<String> response = get(user, "?per_page=abc");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void getTransaction_owned_returns200WithEnvelope() throws Exception {
        TestSession user = testLogin.login("quentin@example.com");
        Transaction saved = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = get(user, "/" + saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("id").asString()).isEqualTo(saved.id().value().toString());
        assertThat(data.get("ticker").asString()).isEqualTo("AAPL");
    }

    @Test
    void getTransaction_otherUsersTransaction_returns404() throws Exception {
        TestSession owner = testLogin.login("rachel@example.com");
        TestSession other = testLogin.login("steve@example.com");
        Transaction saved = transactionService.record(owner.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = get(other, "/" + saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void getTransaction_unknownId_returns404() throws Exception {
        TestSession user = testLogin.login("tina@example.com");

        ResponseEntity<String> response = get(user, "/" + UUID.randomUUID());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void patchTransaction_quantityEdit_returns200WithUpdatedQuantity() throws Exception {
        TestSession user = testLogin.login("uma@example.com");
        Transaction saved = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = patch(user, saved.id().value(), "{\"quantity\":\"7\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("quantity").asString()).isEqualTo("7.000000");
        assertThat(data.get("operation").asString()).isEqualTo("BUY");
        assertThat(data.get("trade_date").asString()).isEqualTo(today().minusDays(1).toString());
    }

    @Test
    void patchTransaction_tradeDateEdit_returns200WithUpdatedTradeDate() throws Exception {
        TestSession user = testLogin.login("victor@example.com");
        LocalDate originalDate = today().minusDays(5);
        Transaction saved = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), originalDate);
        LocalDate newDate = today().minusDays(2);

        ResponseEntity<String> response = patch(user, saved.id().value(), "{\"trade_date\":\"" + newDate + "\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("trade_date").asString()).isEqualTo(newDate.toString());
    }

    @Test
    void patchTransaction_wouldInvalidateLaterSell_returns422ValidationErrorWithSellDetails() throws Exception {
        TestSession user = testLogin.login("wendy@example.com");
        Transaction buy = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(10));
        Transaction sell = transactionService.record(user.userId(), AAPL, Operation.SELL,
                new Quantity(new BigDecimal("8")), today().minusDays(1));

        ResponseEntity<String> response = patch(user, buy.id().value(), "{\"quantity\":\"5\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        JsonNode detail = error.get("details").get(0);
        assertThat(detail.get("field").asString()).isEqualTo("trade_date");
        assertThat(detail.get("code").asString()).isEqualTo("would_invalidate_sell");
        assertThat(detail.get("message").asString()).contains(sell.id().value().toString());
    }

    @Test
    void patchTransaction_selfOversell_returns422InsufficientHoldings() throws Exception {
        TestSession user = testLogin.login("xavier@example.com");
        transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(5));
        Transaction sell = transactionService.record(user.userId(), AAPL, Operation.SELL,
                new Quantity(new BigDecimal("5")), today().minusDays(1));

        ResponseEntity<String> response = patch(user, sell.id().value(), "{\"quantity\":\"50\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("INSUFFICIENT_HOLDINGS");
    }

    @Test
    void patchTransaction_futureDate_returns422TradeDateFuture() throws Exception {
        TestSession user = testLogin.login("yolanda@example.com");
        Transaction saved = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = patch(
                user, saved.id().value(), "{\"trade_date\":\"" + today().plusDays(1) + "\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("TRADE_DATE_FUTURE");
    }

    @Test
    void patchTransaction_notOwned_returns404() throws Exception {
        TestSession owner = testLogin.login("zack@example.com");
        TestSession other = testLogin.login("amy@example.com");
        Transaction saved = transactionService.record(owner.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = patch(other, saved.id().value(), "{\"quantity\":\"5\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void deleteTransaction_noLaterSellDependency_returns204() {
        TestSession user = testLogin.login("bruce@example.com");
        Transaction saved = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = delete(user, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThatThrownBy(() -> transactionService.get(user.userId(), saved.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTransaction_removesBuyWithDependentLaterSell_returns422() throws Exception {
        TestSession user = testLogin.login("carla@example.com");
        Transaction buy = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(10));
        Transaction sell = transactionService.record(user.userId(), AAPL, Operation.SELL,
                new Quantity(new BigDecimal("8")), today().minusDays(1));

        ResponseEntity<String> response = delete(user, buy.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("message").asString()).contains(sell.id().value().toString());
    }

    @Test
    void deleteTransaction_notOwned_returns404() {
        TestSession owner = testLogin.login("dan@example.com");
        TestSession other = testLogin.login("eve@example.com");
        Transaction saved = transactionService.record(owner.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));

        ResponseEntity<String> response = delete(other, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void deleteTransaction_alreadyDeleted_returns404() {
        TestSession user = testLogin.login("felix@example.com");
        Transaction saved = transactionService.record(user.userId(), AAPL, Operation.BUY,
                new Quantity(new BigDecimal("10")), today().minusDays(1));
        delete(user, saved.id().value());

        ResponseEntity<String> response = delete(user, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private String errorCode(ResponseEntity<String> response) {
        return json.readTree(response.getBody()).get("error").get("code").asString();
    }

    private static String transactionBody(String ticker, String operation, String quantity, LocalDate tradeDate) {
        return "{\"ticker\":\"" + ticker + "\",\"operation\":\"" + operation + "\","
                + "\"quantity\":\"" + quantity + "\",\"trade_date\":\"" + tradeDate + "\"}";
    }

    private ResponseEntity<String> post(TestSession authenticatedAs, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(authenticatedAs.headers());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private ResponseEntity<String> get(TestSession authenticatedAs, String pathAndQuery) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + pathAndQuery,
                HttpMethod.GET,
                new HttpEntity<>(authenticatedAs.headers()),
                String.class);
    }

    private ResponseEntity<String> patch(TestSession authenticatedAs, UUID id, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(authenticatedAs.headers());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + id,
                HttpMethod.PATCH,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private ResponseEntity<String> delete(TestSession authenticatedAs, UUID id) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(authenticatedAs.headers()),
                String.class);
    }
}
