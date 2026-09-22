package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class, CsrfIT.PingEndpoint.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfIT {

    private static final String STATE_CHANGING_PATH = "/api/v1/__test/state-changing";
    private static final String SAFE_PATH = "/api/v1/__test/safe";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper json;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void stateChanging_unauthenticated_returns401Unauthorized() {
        ResponseEntity<String> response = http.exchange(
                url(STATE_CHANGING_PATH), HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void stateChangingAuthenticated_missingHeader_returns403Forbidden() {
        TestSession session = testLogin.login("csrf-missing-header@example.com");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.headers().getFirst(HttpHeaders.COOKIE));
        // Deliberately omit X-CSRF-Token.

        ResponseEntity<String> response = http.exchange(
                url(STATE_CHANGING_PATH), HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("FORBIDDEN");
    }

    @Test
    void stateChangingAuthenticated_headerMatchesCookie_returns200() {
        TestSession session = testLogin.login("csrf-match@example.com");

        ResponseEntity<String> response = http.exchange(
                url(STATE_CHANGING_PATH), HttpMethod.POST, new HttpEntity<>(session.headers()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void safeMethodAuthenticated_noCsrfHeader_returns200() {
        TestSession session = testLogin.login("csrf-safe@example.com");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.headers().getFirst(HttpHeaders.COOKIE));

        ResponseEntity<String> response = http.exchange(
                url(SAFE_PATH), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void login_alwaysSetsCsrfCookie() {
        TestSession session = testLogin.login("csrf-on-login@example.com");

        assertThat(session.headers().getFirst(HttpHeaders.COOKIE)).contains("argus_csrf=");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String errorCode(ResponseEntity<String> response) {
        return json.readTree(response.getBody()).get("error").get("code").asString();
    }

    @RestController
    static class PingEndpoint {

        @PostMapping("/__test/state-changing")
        String stateChanging() {
            return "ok";
        }

        @GetMapping("/__test/safe")
        String safe() {
            return "ok";
        }
    }
}
