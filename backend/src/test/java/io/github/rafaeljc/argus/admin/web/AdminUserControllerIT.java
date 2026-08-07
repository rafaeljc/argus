package io.github.rafaeljc.argus.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.CsrfCookieFactory;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserControllerIT {

    private static final String ENDPOINT = "/api/v1/admin/users";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String CSRF_VALUE = "admin-user-it-csrf-token";

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
    private SessionRepository sessionRepository;

    @Test
    void search_noFilters_returnsAllUsersWithEnvelope() throws Exception {
        User admin = seedAdmin("admin1@example.com");
        User other = seedPlain("other1@example.com");

        ResponseEntity<String> response = search(admin, "", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(idsOf(body)).contains(admin.id().value().toString(), other.id().value().toString());
    }

    @Test
    void search_isSuspendedTrue_returnsOnlySuspended() throws Exception {
        User admin = seedAdmin("admin2@example.com");
        User suspended = seedPlain("suspended2@example.com");
        suspend(suspended.id());
        seedPlain("notsuspended2@example.com");

        ResponseEntity<String> response = search(admin, "?is_suspended=true", null);

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(suspended.id().value().toString());
    }

    @Test
    void search_isDeletedTrue_returnsOnlyDeleted() throws Exception {
        User admin = seedAdmin("admin3@example.com");
        User deleted = seedPlain("deleted3@example.com");
        userService.softDelete(deleted.id(), PASSWORD);
        seedPlain("notdeleted3@example.com");

        ResponseEntity<String> response = search(admin, "?is_deleted=true", null);

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(deleted.id().value().toString());
    }

    @Test
    void search_isVerifiedFalse_returnsOnlyUnverified() throws Exception {
        User admin = seedAdmin("admin4@example.com");
        User unverified = userService.createUnverified("unverified4@example.com", PASSWORD);
        seedPlain("verified4@example.com");

        ResponseEntity<String> response = search(admin, "?is_verified=false", null);

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).contains(unverified.id().value().toString());
    }

    @Test
    void search_combinedFilters_appliesAllPredicates() throws Exception {
        User match = seedPlain("acme5@example.com");
        suspend(match.id());
        User wrongEmail = seedPlain("other5@example.com");
        suspend(wrongEmail.id());
        seedPlain("acme5b@example.com");
        User admin = seedAdmin("admin5@example.com");

        ResponseEntity<String> response = search(admin, "?is_suspended=true", "{\"email_contains\":\"acme5\"}");

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(match.id().value().toString());
    }

    @Test
    void search_emailContainsFragment_isCaseInsensitivePartialMatch() throws Exception {
        User admin = seedAdmin("admin6@example.com");
        User target = seedPlain("jane.doe@ACME6.com");
        seedPlain("someone-else6@example.com");

        ResponseEntity<String> response = search(admin, "", "{\"email_contains\":\"acme6\"}");

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(target.id().value().toString());
    }

    @Test
    void search_pagination_populatesMetaAndPreservesFiltersInLinks() throws Exception {
        seedPlain("first7@example.com");
        seedPlain("second7@example.com");
        seedPlain("third7@example.com");
        User admin = seedAdmin("admin7@example.com");

        ResponseEntity<String> response = search(admin, "?is_suspended=false&page=1&per_page=2", null);

        JsonNode body = json.readTree(response.getBody());
        JsonNode meta = body.get("meta");
        assertThat(meta.get("page").asInt()).isEqualTo(1);
        assertThat(meta.get("per_page").asInt()).isEqualTo(2);
        assertThat(body.get("data")).hasSize(2);
        JsonNode links = body.get("links");
        assertThat(links.get("next").asString()).contains("is_suspended=false").contains("page=2");
        assertThat(links.get("self").asString()).contains("is_suspended=false");
    }

    @Test
    void getUser_existingDeletedUser_returns200() throws Exception {
        User admin = seedAdmin("admin9@example.com");
        User deleted = seedPlain("deleted9@example.com");
        userService.softDelete(deleted.id(), PASSWORD);

        ResponseEntity<String> response = get(admin, "/" + deleted.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("id").asString()).isEqualTo(deleted.id().value().toString());
        assertThat(data.get("is_deleted").asBoolean()).isTrue();
    }

    @Test
    void getUser_unknownId_returns404() throws Exception {
        User admin = seedAdmin("admin10@example.com");

        ResponseEntity<String> response = get(admin, "/" + UuidCreator.getTimeOrderedEpoch());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
    }

    private static List<String> idsOf(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.get("data").forEach(node -> ids.add(node.get("id").asString()));
        return ids;
    }

    private ResponseEntity<String> search(User authenticatedAs, String query, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        headers.add("X-CSRF-Token", CSRF_VALUE);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + query,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
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

    private User seedAdmin(String email) {
        User u = userService.createUnverified(email, PASSWORD);
        User verified = userService.markVerified(u.id());
        Instant now = verified.createdAt();
        return userRepository.save(new User(verified.id(), verified.email(), verified.passwordHash(),
                verified.isVerified(), verified.isSuspended(), verified.isDeleted(), true,
                now, now, null));
    }

    private User seedPlain(String email) {
        User u = userService.createUnverified(email, PASSWORD);
        return userService.markVerified(u.id());
    }

    private void suspend(UserId id) {
        userRepository.save(suspendedCopy(userRepository.findById(id).orElseThrow()));
    }

    private static User suspendedCopy(User user) {
        return new User(user.id(), user.email(), user.passwordHash(), user.isVerified(), true,
                user.isDeleted(), user.isAdmin(), user.createdAt(), user.updatedAt(), user.deletedAt());
    }

    private String seedSession(User user) {
        String token = "admin-user-it-session-" + UuidCreator.getTimeOrderedEpoch();
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
