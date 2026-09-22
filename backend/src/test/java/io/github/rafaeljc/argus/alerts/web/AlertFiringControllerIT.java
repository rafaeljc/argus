package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertFiringControllerIT {

    private static final String ENDPOINT = "/api/v1/alert-firings";

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
    private AlertFiringRepository firingRepository;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void getAlertFirings_authenticated_returnsOwnedPageWithEnvelope() throws Exception {
        TestSession user = testLogin.login("alice-firing@example.com");
        seedFiring(user.userId(), Instant.parse("2026-01-01T00:00:00Z"));
        seedFiring(user.userId(), Instant.parse("2026-02-01T00:00:00Z"));

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
    void getAlertFirings_ordersNewestFirst() throws Exception {
        TestSession user = testLogin.login("bob-firing@example.com");
        AlertFiring oldest = seedFiring(user.userId(), Instant.parse("2026-01-01T00:00:00Z"));
        AlertFiring newest = seedFiring(user.userId(), Instant.parse("2026-03-01T00:00:00Z"));

        ResponseEntity<String> response = get(user, "");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get(0).get("id").asString()).isEqualTo(newest.id().value().toString());
        assertThat(data.get(1).get("id").asString()).isEqualTo(oldest.id().value().toString());
    }

    @Test
    void getAlertFirings_empty_returnsEmptyDataWithZeroMeta() throws Exception {
        TestSession user = testLogin.login("carol-firing@example.com");

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
    void getAlertFirings_perPageOne_secondPage_setsNextPrevLast() throws Exception {
        TestSession user = testLogin.login("dave-firing@example.com");
        seedFiring(user.userId(), Instant.parse("2026-01-01T00:00:00Z"));
        seedFiring(user.userId(), Instant.parse("2026-02-01T00:00:00Z"));
        seedFiring(user.userId(), Instant.parse("2026-03-01T00:00:00Z"));

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
    void getAlertFirings_onlyReturnsCallersFirings() throws Exception {
        TestSession owner = testLogin.login("erin-firing@example.com");
        TestSession other = testLogin.login("frank-firing@example.com");
        seedFiring(owner.userId(), Instant.parse("2026-01-01T00:00:00Z"));
        seedFiring(other.userId(), Instant.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<String> response = get(owner, "");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data).hasSize(1);
    }

    @Test
    void getAlertFirings_perPageAboveMax_returns422() throws Exception {
        TestSession user = testLogin.login("grace-firing@example.com");

        ResponseEntity<String> response = get(user, "?per_page=201");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("per_page");
    }

    @Test
    void getAlertFirings_responseFields_matchContractShape() throws Exception {
        TestSession user = testLogin.login("heidi-firing@example.com");
        AlertFiring saved = seedFiring(user.userId(), Instant.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<String> response = get(user, "");

        JsonNode item = json.readTree(response.getBody()).get("data").get(0);
        assertThat(item.get("id").asString()).isEqualTo(saved.id().value().toString());
        assertThat(item.get("rule_id").asString()).isEqualTo(saved.ruleId().value().toString());
        assertThat(item.has("user_id")).isFalse();
        assertThat(item.get("direction").asString()).isEqualTo("UP");
        assertThat(item.get("portfolio_value_start").asString()).isEqualTo("1000.00");
        assertThat(item.get("portfolio_value_end").asString()).isEqualTo("1050.00");
    }

    private AlertFiring seedFiring(UserId userId, Instant firedAt) {
        return firingRepository.insert(new AlertFiring(
                new FiringId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                new RuleId(UuidCreator.getTimeOrderedEpoch()),
                Direction.UP,
                new Percentage(new BigDecimal("5.0")),
                new AlertLookbackWindow(30),
                firedAt,
                new Money(new BigDecimal("1000.00")),
                new Money(new BigDecimal("1050.00")),
                new BigDecimal("5.00"),
                LocalDate.parse("2025-12-01"),
                LocalDate.parse("2026-01-01")));
    }

    private ResponseEntity<String> get(TestSession authenticatedAs, String pathAndQuery) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + pathAndQuery,
                HttpMethod.GET,
                new HttpEntity<>(authenticatedAs.headers()),
                String.class);
    }
}
