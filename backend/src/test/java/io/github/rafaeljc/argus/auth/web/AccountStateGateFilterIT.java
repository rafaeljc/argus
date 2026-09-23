package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class, AccountStateGateFilterIT.GatedEndpoints.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountStateGateFilterIT {

    private static final String PROTECTED_PATH = "/api/v1/__test/protected";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void deletedUser_returns401Unauthorized() {
        TestSession session = testLogin.login("gate-deleted@example.com");
        flipState(session, true, false, true);

        ResponseEntity<String> response = getWithSession(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void suspendedUser_returns403AccountSuspended() {
        TestSession session = testLogin.login("gate-suspended@example.com");
        flipState(session, true, true, false);

        ResponseEntity<String> response = getWithSession(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("ACCOUNT_SUSPENDED");
    }

    @Test
    void unverifiedUser_returns403EmailNotVerified() {
        TestSession session = testLogin.login("gate-unverified@example.com");
        flipState(session, false, false, false);

        ResponseEntity<String> response = getWithSession(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void activeVerifiedUser_returns200() {
        TestSession session = testLogin.login("gate-active@example.com");

        ResponseEntity<String> response = getWithSession(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // Flips state via the repository, not the API — the login above already required an active,
    // verified account, so this simulates the account changing state *after* the session exists.
    private void flipState(TestSession session, boolean verified, boolean suspended, boolean deleted) {
        User base = userService.lookup(session.userId());
        Instant now = base.createdAt();
        userRepository.save(new User(session.userId(), base.email(), base.passwordHash(),
                verified, suspended, deleted, base.isAdmin(),
                now, now, deleted ? now : null));
    }

    private ResponseEntity<String> getWithSession(TestSession session) {
        return http.exchange(url(PROTECTED_PATH), HttpMethod.GET, new HttpEntity<>(session.headers()), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String errorCode(ResponseEntity<String> response) {
        return json.readTree(response.getBody()).get("error").get("code").asString();
    }

    @RestController
    static class GatedEndpoints {

        @GetMapping("/__test/protected")
        String protectedEndpoint() {
            return "ok";
        }
    }
}
