package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.alerts.application.AlertService;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import java.math.BigDecimal;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertRuleControllerIT {

    private static final String ENDPOINT = "/api/v1/alert-rules";

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
    private AlertService alertService;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void postAlertRules_validRequest_returns201WithLocationAndEnvelope() throws Exception {
        TestSession user = testLogin.login("alice@example.com");

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "5.0", 30));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).matches(".*/api/v1/alert-rules/[0-9a-fA-F-]{36}$");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("direction").asString()).isEqualTo("UP");
        assertThat(data.get("threshold").asDouble()).isEqualTo(5.0);
        assertThat(data.get("window_days").asInt()).isEqualTo(30);
        assertThat(data.get("id").asString()).isNotBlank();
        assertThat(data.get("created_at").asString()).isNotBlank();
    }

    @Test
    void postAlertRules_twentyExistingRules_returns422TooManyRules() throws Exception {
        TestSession user = testLogin.login("bob@example.com");
        for (int i = 1; i <= 20; i++) {
            alertService.create(
                    user.userId(), Direction.UP, new Percentage(new BigDecimal(i + ".0")),
                    new AlertLookbackWindow(30));
        }

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "50.0", 30));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("TOO_MANY_RULES");
    }

    @Test
    void postAlertRules_duplicateSignature_returns409DuplicateRule() throws Exception {
        TestSession user = testLogin.login("carol@example.com");
        alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "5.0", 30));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(response)).isEqualTo("DUPLICATE_RULE");
    }

    @Test
    void postAlertRules_invalidWindowDays_returns422ValidationErrorWithWindowDaysField() throws Exception {
        TestSession user = testLogin.login("dave@example.com");

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "5.0", 15));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("window_days");
    }

    @Test
    void deleteAlertRule_ownedRule_returns204() {
        TestSession user = testLogin.login("erin@example.com");
        AlertRule saved = alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = delete(user, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteAlertRule_notOwned_returns404() throws Exception {
        TestSession owner = testLogin.login("frank@example.com");
        TestSession other = testLogin.login("grace@example.com");
        AlertRule saved = alertService.create(
                owner.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = delete(other, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void deleteAlertRule_alreadyCancelled_returns404() throws Exception {
        TestSession user = testLogin.login("heidi@example.com");
        AlertRule saved = alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));
        delete(user, saved.id().value());

        ResponseEntity<String> response = delete(user, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void getAlertRules_authenticated_returnsOwnedPageWithEnvelope() throws Exception {
        TestSession user = testLogin.login("ivan@example.com");
        alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));
        alertService.create(
                user.userId(), Direction.DOWN, new Percentage(new BigDecimal("10.0")), new AlertLookbackWindow(90));

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
        assertThat(links.get("next").isNull()).isTrue();
        assertThat(links.get("prev").isNull()).isTrue();
    }

    @Test
    void getAlertRules_empty_returnsEmptyDataWithZeroMeta() throws Exception {
        TestSession user = testLogin.login("judy2@example.com");

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
    void getAlertRules_perPageOne_secondPage_setsNextPrevLast() throws Exception {
        TestSession user = testLogin.login("kevin2@example.com");
        alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("1.0")), new AlertLookbackWindow(30));
        alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("2.0")), new AlertLookbackWindow(30));
        alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("3.0")), new AlertLookbackWindow(30));

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
    void getAlertRules_onlyReturnsCallersRules() throws Exception {
        TestSession owner = testLogin.login("laura2@example.com");
        TestSession other = testLogin.login("mike2@example.com");
        alertService.create(
                owner.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));
        alertService.create(
                other.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = get(owner, "");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data).hasSize(1);
    }

    @Test
    void getAlertRules_perPageAboveMax_returns422() throws Exception {
        TestSession user = testLogin.login("nina2@example.com");

        ResponseEntity<String> response = get(user, "?per_page=201");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("per_page");
    }

    @Test
    void getAlertRule_owned_returns200WithEnvelope() throws Exception {
        TestSession user = testLogin.login("oscar2@example.com");
        AlertRule saved = alertService.create(
                user.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = get(user, "/" + saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("id").asString()).isEqualTo(saved.id().value().toString());
        assertThat(data.get("direction").asString()).isEqualTo("UP");
    }

    @Test
    void getAlertRule_otherUsersRule_returns404() throws Exception {
        TestSession owner = testLogin.login("oliver2@example.com");
        TestSession other = testLogin.login("peggy2@example.com");
        AlertRule saved = alertService.create(
                owner.userId(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = get(other, "/" + saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void getAlertRule_unknownId_returns404() throws Exception {
        TestSession user = testLogin.login("quentin2@example.com");

        ResponseEntity<String> response = get(user, "/" + UUID.randomUUID());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    private String errorCode(ResponseEntity<String> response) {
        return json.readTree(response.getBody()).get("error").get("code").asString();
    }

    private static String alertRuleBody(String direction, String threshold, int windowDays) {
        return "{\"direction\":\"" + direction + "\",\"threshold\":" + threshold
                + ",\"window_days\":" + windowDays + "}";
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

    private ResponseEntity<String> delete(TestSession authenticatedAs, UUID id) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(authenticatedAs.headers()),
                String.class);
    }
}
