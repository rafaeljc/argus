package io.github.rafaeljc.argus.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
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
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthorizationIT {

    private static final String ENDPOINT = "/api/v1/admin/eod-pipeline/runs";
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
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    void get_noSession_returns401() {
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void get_authenticatedNonAdmin_returns403ForbiddenEnvelope() throws Exception {
        User nonAdmin = seedVerified("non-admin@example.com", false);

        ResponseEntity<String> response = get(nonAdmin);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void get_authenticatedAdmin_isNotForbidden() {
        User admin = seedVerified("admin@example.com", true);

        ResponseEntity<String> response = get(admin);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> get(User authenticatedAs) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionToken);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private User seedVerified(String email, boolean admin) {
        User u = userService.createUnverified(email, PASSWORD);
        User verified = userService.markVerified(u.id());
        if (!admin) {
            return verified;
        }
        Instant now = verified.createdAt();
        return userRepository.save(new User(verified.id(), verified.email(), verified.passwordHash(),
                verified.isVerified(), verified.isSuspended(), verified.isDeleted(), true,
                now, now, null));
    }

    private String seedSession(User user) {
        String token = "admin-authz-it-session-" + UuidCreator.getTimeOrderedEpoch();
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
