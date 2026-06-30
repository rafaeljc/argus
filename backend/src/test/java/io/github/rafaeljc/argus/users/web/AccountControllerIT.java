package io.github.rafaeljc.argus.users.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.infrastructure.security.CsrfCookieFactory;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, TestCurrentUserIdConfig.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerIT {

    private static final String ENDPOINT = "/api/v1/account/me";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String CSRF_VALUE = "account-it-csrf-token";

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

    @Test
    void getMe_authenticatedUser_returns200WithSafeAccountEnvelope() throws Exception {
        User seeded = userService.createUnverified("alice@example.com", PASSWORD);

        ResponseEntity<String> response = exchange(seeded, HttpMethod.GET, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");

        String body = response.getBody();

        // Schema columns that must never reach the wire (see V1 migration `users` table).
        assertThat(body)
                .doesNotContain("password_hash")
                .doesNotContain("is_suspended")
                .doesNotContain("is_deleted")
                .doesNotContain("updated_at")
                .doesNotContain("deleted_at");

        JsonNode root = json.readTree(body);
        assertThat(root.propertyNames()).containsExactly("data");

        JsonNode data = root.get("data");
        assertThat(data.propertyNames()).containsExactlyInAnyOrder(
                "id", "email", "is_verified", "is_admin", "created_at");
        assertThat(data.get("id").asString()).isEqualTo(seeded.id().value().toString());
        assertThat(data.get("email").asString()).isEqualTo(seeded.email());
        assertThat(data.get("is_verified").asBoolean()).isEqualTo(seeded.isVerified());
        assertThat(data.get("is_admin").asBoolean()).isEqualTo(seeded.isAdmin());
        assertThat(data.get("created_at").asString()).isEqualTo(seeded.createdAt().toString());
    }

    @Test
    void deleteMe_authenticatedUserWithCorrectPassword_returns204AndSoftDeletes() {
        User seeded = userService.createUnverified("bob@example.com", PASSWORD);

        ResponseEntity<String> response = exchange(seeded, HttpMethod.DELETE, passwordBody(PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();

        User after = userService.lookup(seeded.id());
        assertThat(after.isDeleted()).isTrue();
        assertThat(after.deletedAt()).isNotNull();
    }

    @Test
    void deleteMe_secondCallOnDeletedUser_returns404AndPreservesDeletedAt() throws Exception {
        User seeded = userService.createUnverified("carol@example.com", PASSWORD);

        ResponseEntity<String> first = exchange(seeded, HttpMethod.DELETE, passwordBody(PASSWORD));
        assertThat(first.getStatusCode().value()).isEqualTo(204);
        Instant firstDeletedAt = userService.lookup(seeded.id()).deletedAt();
        assertThat(firstDeletedAt).isNotNull();

        ResponseEntity<String> second = exchange(seeded, HttpMethod.DELETE, passwordBody(PASSWORD));
        assertThat(second.getStatusCode().value()).isEqualTo(404);
        assertThat(json.readTree(second.getBody()).get("error").get("code").asString())
                .isEqualTo("NOT_FOUND");
        assertThat(userService.lookup(seeded.id()).deletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void deleteMe_wrongPasswordOnDeletedUser_returns404NotFoundForAntiEnumeration() throws Exception {
        User seeded = userService.createUnverified("frank@example.com", PASSWORD);
        userService.softDelete(seeded.id(), PASSWORD);

        ResponseEntity<String> response = exchange(seeded, HttpMethod.DELETE, passwordBody("anything"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void deleteMe_wrongPassword_returns422InvalidCurrentPasswordAndDoesNotDelete() throws Exception {
        User seeded = userService.createUnverified("dave@example.com", PASSWORD);

        ResponseEntity<String> response = exchange(seeded, HttpMethod.DELETE, passwordBody("not the password"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("INVALID_CURRENT_PASSWORD");

        User after = userService.lookup(seeded.id());
        assertThat(after.isDeleted()).isFalse();
        assertThat(after.deletedAt()).isNull();
    }

    @Test
    void deleteMe_blankPassword_returns422ValidationErrorWithCurrentPasswordField() throws Exception {
        User seeded = userService.createUnverified("erin@example.com", PASSWORD);

        ResponseEntity<String> response = exchange(seeded, HttpMethod.DELETE, passwordBody(""));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details")).hasSize(1);
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("current_password");

        User after = userService.lookup(seeded.id());
        assertThat(after.isDeleted()).isFalse();
    }

    private static String passwordBody(String currentPassword) {
        return "{\"current_password\":\"" + currentPassword + "\"}";
    }

    private ResponseEntity<String> exchange(User authenticatedAs, HttpMethod method, String jsonBody) {
        String sessionToken = seedSession(authenticatedAs);
        HttpHeaders headers = new HttpHeaders();
        headers.add(TestCurrentUserIdConfig.HEADER, authenticatedAs.id().value().toString());
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        headers.add("X-CSRF-Token", CSRF_VALUE);
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                method,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private String seedSession(User user) {
        String token = "account-it-session-" + UuidCreator.getTimeOrderedEpoch();
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
