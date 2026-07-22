package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({PostgresContainer.class, CsrfFilterIT.PingEndpoint.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfFilterIT {

    private static final String RAW_SESSION_TOKEN = "csrf-it-session-token";
    private static final String CSRF_VALUE = "csrf-it-token-value";
    private static final String STATE_CHANGING_PATH = "/api/v1/__test/state-changing";
    private static final String SAFE_PATH = "/api/v1/__test/safe";

    @LocalServerPort
    private int port;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TestRestTemplate http;

    @Test
    void stateChanging_unauthenticated_returns401Unauthorized() {
        ResponseEntity<String> response = postWithCookies(STATE_CHANGING_PATH, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void stateChangingAuthenticated_missingHeader_returns403Forbidden() {
        seedActiveSession();
        ResponseEntity<String> response = postWithCookies(STATE_CHANGING_PATH, RAW_SESSION_TOKEN, CSRF_VALUE, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("FORBIDDEN");
    }

    @Test
    void stateChangingAuthenticated_headerMatchesCookie_returns200() {
        seedActiveSession();
        ResponseEntity<String> response =
                postWithCookies(STATE_CHANGING_PATH, RAW_SESSION_TOKEN, CSRF_VALUE, CSRF_VALUE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void safeMethodAuthenticated_noCsrfHeader_returns200() {
        seedActiveSession();
        ResponseEntity<String> response = getWithSessionCookie(SAFE_PATH, RAW_SESSION_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void seedActiveSession() {
        UserId userId = userService.createUnverified(
                "csrf-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
        // Promote past the state gate so this IT isolates CSRF behavior.
        userRepository.save(activeUser(userId, userService.lookup(userId)));

        Instant now = Instant.now();
        sessionRepository.save(new Session(
                new SessionId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                sha256Hex(RAW_SESSION_TOKEN),
                "10.0.0.1",
                "IT-Agent",
                now,
                now.plus(Duration.ofDays(30)),
                now));
    }

    private static User activeUser(UserId userId, User base) {
        return new User(userId, base.email(), base.passwordHash(),
                true, false, false, base.isAdmin(),
                base.createdAt(), base.createdAt(), null);
    }

    private ResponseEntity<String> postWithCookies(String path, String sessionToken, String csrfCookie,
                                                   String csrfHeader) {
        HttpHeaders headers = new HttpHeaders();
        StringBuilder cookies = new StringBuilder();
        if (sessionToken != null) {
            cookies.append(SessionCookieFactory.COOKIE_NAME).append('=').append(sessionToken);
        }
        if (csrfCookie != null) {
            if (cookies.length() > 0) {
                cookies.append("; ");
            }
            cookies.append(CsrfCookieFactory.COOKIE_NAME).append('=').append(csrfCookie);
        }
        if (cookies.length() > 0) {
            headers.add(HttpHeaders.COOKIE, cookies.toString());
        }
        if (csrfHeader != null) {
            headers.add("X-CSRF-Token", csrfHeader);
        }
        return http.exchange(url(path), HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> getWithSessionCookie(String path, String sessionToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionToken);
        return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String errorCode(ResponseEntity<String> response) {
        try {
            JsonNode body = new ObjectMapper().readTree(response.getBody());
            return body.path("error").path("code").asText();
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse error body: " + response.getBody(), e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
