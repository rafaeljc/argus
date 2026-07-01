package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

    private static final String ENDPOINT = "/api/v1/auth/signup";
    private static final String VALID_PASSWORD = "correct horse battery staple";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postSignup_validRequest_returns201WithLocationAndUserIdAndSeedsOutbox() throws Exception {
        String email = "alice@example.com";

        ResponseEntity<String> response = post(body(email, VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/account/me");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.propertyNames()).containsExactlyInAnyOrder("user_id", "verification_sent");
        assertThat(data.get("verification_sent").asBoolean()).isTrue();
        UUID userId = UUID.fromString(data.get("user_id").asString());

        Map<String, Object> userRow = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", userId);
        assertThat(userRow.get("email")).isEqualTo(email);
        assertThat(userRow.get("is_verified")).isEqualTo(false);
        assertThat(userRow.get("is_suspended")).isEqualTo(false);
        assertThat(userRow.get("is_deleted")).isEqualTo(false);

        Map<String, Object> verificationRow = jdbc.queryForMap(
                "SELECT * FROM email_verifications WHERE user_id = ?", userId);
        assertThat(verificationRow.get("token_hash")).asString().matches("[0-9a-f]{64}");
        assertThat(verificationRow.get("verified_at")).isNull();

        Map<String, Object> outboxRow = jdbc.queryForMap(
                "SELECT event_type, idempotence_key, payload::text AS payload "
                        + "FROM outbox WHERE aggregate_id = ?",
                userId);
        assertThat(outboxRow.get("event_type")).isEqualTo("email.verification");
        assertThat(outboxRow.get("idempotence_key")).asString().startsWith("email.verification:");
        JsonNode payload = json.readTree((String) outboxRow.get("payload"));
        assertThat(payload.get("user_id").asString()).isEqualTo(userId.toString());
        assertThat(payload.get("email").asString()).isEqualTo(email);
        assertThat(payload.get("token").asString()).matches("[A-Za-z0-9_-]+");
        assertThat(payload.get("expires_at").asString()).isNotEmpty();
        // The token in the outbox is the plain form (goes into the email link); DB stores only the hash.
        assertThat(payload.get("token").asString()).isNotEqualTo(verificationRow.get("token_hash"));
    }

    @Test
    void postSignup_malformedEmail_returns422ValidationErrorWithEmailField() throws Exception {
        ResponseEntity<String> response = post(body("not-an-email", VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(fieldNames(error.get("details"))).contains("email");
    }

    @Test
    void postSignup_shortPassword_returns422ValidationErrorWithPasswordField() throws Exception {
        ResponseEntity<String> response = post(body("bob@example.com", "short"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(fieldNames(error.get("details"))).contains("password");
    }

    @Test
    void postSignup_duplicateEmail_returns422ValidationErrorWithAlreadyTakenCode() throws Exception {
        String email = "carol@example.com";
        userService.createUnverified(email, VALID_PASSWORD);

        ResponseEntity<String> response = post(body(email, VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        JsonNode details = error.get("details");
        assertThat(details).hasSize(1);
        assertThat(details.get(0).get("field").asString()).isEqualTo("email");
        assertThat(details.get(0).get("code").asString()).isEqualTo("already_taken");
    }

    @Test
    void postSignup_emailWithMixedCase_persistsLowercasedForm() throws Exception {
        // The User domain lowercases on construction so lookups and the unique index treat
        // "Dave@Example.COM" and "dave@example.com" as the same address.
        ResponseEntity<String> response = post(body("Dave@Example.COM", VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        UUID userId = UUID.fromString(
                json.readTree(response.getBody()).get("data").get("user_id").asString());
        String storedEmail = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, userId);
        assertThat(storedEmail).isEqualTo("dave@example.com");
    }

    private static String body(String email, String password) {
        // Hand-built JSON avoids leaking test-side serialization choices into the wire format.
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private ResponseEntity<String> post(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private static java.util.List<String> fieldNames(JsonNode details) {
        java.util.List<String> names = new java.util.ArrayList<>();
        details.forEach(node -> names.add(node.get("field").asString()));
        return names;
    }
}
