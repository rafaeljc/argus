package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.port.AlertFiringRepository;
import io.github.rafaeljc.argus.alerts.domain.AlertFiring;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.FiringId;
import io.github.rafaeljc.argus.common.domain.Money;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.RuleId;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
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
class AlertFiringControllerIT {

    private static final String ENDPOINT = "/api/v1/alert-firings";
    private static final String PASSWORD = "correct horse battery staple";

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
    private AlertFiringRepository firingRepository;

    @Test
    void getAlertFirings_authenticated_returnsOwnedPageWithEnvelope() throws Exception {
        User user = seedVerified("alice-firing@example.com");
        seedFiring(user.id(), Instant.parse("2026-01-01T00:00:00Z"));
        seedFiring(user.id(), Instant.parse("2026-02-01T00:00:00Z"));

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
        User user = seedVerified("bob-firing@example.com");
        AlertFiring oldest = seedFiring(user.id(), Instant.parse("2026-01-01T00:00:00Z"));
        AlertFiring newest = seedFiring(user.id(), Instant.parse("2026-03-01T00:00:00Z"));

        ResponseEntity<String> response = get(user, "");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get(0).get("id").asString()).isEqualTo(newest.id().value().toString());
        assertThat(data.get(1).get("id").asString()).isEqualTo(oldest.id().value().toString());
    }

    @Test
    void getAlertFirings_empty_returnsEmptyDataWithZeroMeta() throws Exception {
        User user = seedVerified("carol-firing@example.com");

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
        User user = seedVerified("dave-firing@example.com");
        seedFiring(user.id(), Instant.parse("2026-01-01T00:00:00Z"));
        seedFiring(user.id(), Instant.parse("2026-02-01T00:00:00Z"));
        seedFiring(user.id(), Instant.parse("2026-03-01T00:00:00Z"));

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
        User owner = seedVerified("erin-firing@example.com");
        User other = seedVerified("frank-firing@example.com");
        seedFiring(owner.id(), Instant.parse("2026-01-01T00:00:00Z"));
        seedFiring(other.id(), Instant.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<String> response = get(owner, "");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data).hasSize(1);
    }

    @Test
    void getAlertFirings_perPageAboveMax_returns422() throws Exception {
        User user = seedVerified("grace-firing@example.com");

        ResponseEntity<String> response = get(user, "?per_page=201");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("per_page");
    }

    @Test
    void getAlertFirings_responseFields_matchContractShape() throws Exception {
        User user = seedVerified("heidi-firing@example.com");
        AlertFiring saved = seedFiring(user.id(), Instant.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<String> response = get(user, "");

        JsonNode item = json.readTree(response.getBody()).get("data").get(0);
        assertThat(item.get("id").asString()).isEqualTo(saved.id().value().toString());
        assertThat(item.get("rule_id").asString()).isEqualTo(saved.ruleId().value().toString());
        assertThat(item.has("user_id")).isFalse();
        assertThat(item.get("direction").asString()).isEqualTo("UP");
        assertThat(item.get("portfolio_value_start").asString()).isEqualTo("1000.00");
        assertThat(item.get("portfolio_value_end").asString()).isEqualTo("1050.00");
    }

    private User seedVerified(String email) {
        User u = userService.createUnverified(email, PASSWORD);
        return userService.markVerified(u.id());
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

    private ResponseEntity<String> get(User authenticatedAs, String pathAndQuery) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionToken);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + pathAndQuery,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private String seedSession(User user) {
        String token = "alert-firings-it-session-" + UuidCreator.getTimeOrderedEpoch();
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
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
