package io.github.rafaeljc.argus.users.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerIT {

    private static final String ENDPOINT = "/api/v1/account/me";

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

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void getMe_authenticatedUser_returns200WithSafeAccountEnvelope() throws Exception {
        TestSession session = testLogin.login("alice@example.com");
        User seeded = userService.lookup(session.userId());

        ResponseEntity<String> response = exchange(session, HttpMethod.GET, null);

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
        TestSession session = testLogin.login("bob@example.com");

        ResponseEntity<String> response = exchange(session, HttpMethod.DELETE, passwordBody(TestLogin.PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();

        User after = userService.lookup(session.userId());
        assertThat(after.isDeleted()).isTrue();
        assertThat(after.deletedAt()).isNotNull();
    }

    @Test
    void deleteMe_secondCallOnDeletedUser_returns401AndPreservesDeletedAt() throws Exception {
        TestSession session = testLogin.login("carol@example.com");

        ResponseEntity<String> first = exchange(session, HttpMethod.DELETE, passwordBody(TestLogin.PASSWORD));
        assertThat(first.getStatusCode().value()).isEqualTo(204);
        Instant firstDeletedAt = userService.lookup(session.userId()).deletedAt();
        assertThat(firstDeletedAt).isNotNull();

        ResponseEntity<String> second = exchange(session, HttpMethod.DELETE, passwordBody(TestLogin.PASSWORD));
        assertThat(second.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(second.getBody()).get("error").get("code").asString())
                .isEqualTo("UNAUTHORIZED");
        assertThat(userService.lookup(session.userId()).deletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void deleteMe_wrongPasswordOnDeletedUser_returns401UnauthorizedForAntiEnumeration() throws Exception {
        TestSession session = testLogin.login("frank@example.com");
        userService.softDelete(session.userId(), TestLogin.PASSWORD);

        ResponseEntity<String> response = exchange(session, HttpMethod.DELETE, passwordBody("anything"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void deleteMe_wrongPassword_returns422InvalidCurrentPasswordAndDoesNotDelete() throws Exception {
        TestSession session = testLogin.login("dave@example.com");

        ResponseEntity<String> response = exchange(session, HttpMethod.DELETE, passwordBody("not the password"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("INVALID_CURRENT_PASSWORD");

        User after = userService.lookup(session.userId());
        assertThat(after.isDeleted()).isFalse();
        assertThat(after.deletedAt()).isNull();
    }

    @Test
    void deleteMe_blankPassword_returns422ValidationErrorWithCurrentPasswordField() throws Exception {
        TestSession session = testLogin.login("erin@example.com");

        ResponseEntity<String> response = exchange(session, HttpMethod.DELETE, passwordBody(""));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.get("details")).hasSize(1);
        assertThat(error.get("details").get(0).get("field").asString()).isEqualTo("current_password");

        User after = userService.lookup(session.userId());
        assertThat(after.isDeleted()).isFalse();
    }

    @Test
    void deleteMe_thenReusingOriginalSessionCookie_clearsCookiesAndRejectsFollowUpRequest()
            throws Exception {
        TestSession session = testLogin.login("heidi@example.com");

        ResponseEntity<String> deleteResponse =
                exchange(session, HttpMethod.DELETE, passwordBody(TestLogin.PASSWORD));
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);

        List<String> setCookieHeaders = deleteResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).isNotNull();
        assertThat(setCookieHeaders).anySatisfy(header -> assertThat(header).startsWith("argus_session=;"));

        ResponseEntity<String> reuse = exchange(session, HttpMethod.GET, null);
        assertThat(reuse.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(reuse.getBody()).get("error").get("code").asString())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void deleteMe_suspendedLiveSession_returns403AccountSuspended() throws Exception {
        TestSession session = testLogin.login("gina@example.com");
        User seeded = userService.lookup(session.userId());
        userRepository.save(new User(seeded.id(), seeded.email(), seeded.passwordHash(),
                true, true, false, seeded.isAdmin(),
                seeded.createdAt(), seeded.updatedAt(), null));

        ResponseEntity<String> response = exchange(session, HttpMethod.DELETE, passwordBody(TestLogin.PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("ACCOUNT_SUSPENDED");
        User after = userService.lookup(session.userId());
        assertThat(after.isDeleted()).isFalse();
    }

    private static String passwordBody(String currentPassword) {
        return "{\"current_password\":\"" + currentPassword + "\"}";
    }

    private ResponseEntity<String> exchange(TestSession session, HttpMethod method, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(session.headers());
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                method,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }
}
