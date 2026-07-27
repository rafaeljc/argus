package io.github.rafaeljc.argus.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.alerts.application.AlertService;
import io.github.rafaeljc.argus.alerts.domain.AlertLookbackWindow;
import io.github.rafaeljc.argus.alerts.domain.AlertRule;
import io.github.rafaeljc.argus.alerts.domain.Direction;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.CsrfCookieFactory;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.Percentage;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
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

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertRuleControllerIT {

    private static final String ENDPOINT = "/api/v1/alert-rules";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String CSRF_VALUE = "alert-rules-it-csrf-token";

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
    private AlertService alertService;

    @Test
    void postAlertRules_validRequest_returns201WithLocationAndEnvelope() throws Exception {
        User user = seedVerified("alice@example.com");

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
        User user = seedVerified("bob@example.com");
        for (int i = 1; i <= 20; i++) {
            alertService.create(
                    user.id(), Direction.UP, new Percentage(new BigDecimal(i + ".0")),
                    new AlertLookbackWindow(30));
        }

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "50.0", 30));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(response)).isEqualTo("TOO_MANY_RULES");
    }

    @Test
    void postAlertRules_duplicateSignature_returns409DuplicateRule() throws Exception {
        User user = seedVerified("carol@example.com");
        alertService.create(
                user.id(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "5.0", 30));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(response)).isEqualTo("DUPLICATE_RULE");
    }

    @Test
    void postAlertRules_invalidWindowDays_returns422ValidationErrorWithWindowDaysField() throws Exception {
        User user = seedVerified("dave@example.com");

        ResponseEntity<String> response = post(user, alertRuleBody("UP", "5.0", 15));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("window_days");
    }

    @Test
    void deleteAlertRule_ownedRule_returns204() {
        User user = seedVerified("erin@example.com");
        AlertRule saved = alertService.create(
                user.id(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = delete(user, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteAlertRule_notOwned_returns404() throws Exception {
        User owner = seedVerified("frank@example.com");
        User other = seedVerified("grace@example.com");
        AlertRule saved = alertService.create(
                owner.id(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));

        ResponseEntity<String> response = delete(other, saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    void deleteAlertRule_alreadyCancelled_returns404() throws Exception {
        User user = seedVerified("heidi@example.com");
        AlertRule saved = alertService.create(
                user.id(), Direction.UP, new Percentage(new BigDecimal("5.0")), new AlertLookbackWindow(30));
        delete(user, saved.id().value());

        ResponseEntity<String> response = delete(user, saved.id().value());

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

    private User seedVerified(String email) {
        User u = userService.createUnverified(email, PASSWORD);
        return userService.markVerified(u.id());
    }

    private ResponseEntity<String> post(User authenticatedAs, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        headers.add("X-CSRF-Token", CSRF_VALUE);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private ResponseEntity<String> delete(User authenticatedAs, UUID id) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        headers.add("X-CSRF-Token", CSRF_VALUE);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                String.class);
    }

    private String seedSession(User user) {
        String token = "alert-rules-it-session-" + UuidCreator.getTimeOrderedEpoch();
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
