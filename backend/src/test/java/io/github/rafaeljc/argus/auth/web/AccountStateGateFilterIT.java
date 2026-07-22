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
import org.springframework.web.bind.annotation.RestController;

@Import({PostgresContainer.class, AccountStateGateFilterIT.GatedEndpoints.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountStateGateFilterIT {

    private static final String RAW_SESSION_TOKEN = "state-gate-it-token";
    private static final String PROTECTED_PATH = "/api/v1/__test/protected";

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
    void deletedUser_returns401Unauthorized() {
        seedSessionFor(saveUser(false, false, true));

        ResponseEntity<String> response = getWithSession(PROTECTED_PATH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode(response)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void suspendedUser_returns403AccountSuspended() {
        seedSessionFor(saveUser(true, true, false));

        ResponseEntity<String> response = getWithSession(PROTECTED_PATH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("ACCOUNT_SUSPENDED");
    }

    @Test
    void unverifiedUser_returns403EmailNotVerified() {
        seedSessionFor(saveUser(false, false, false));

        ResponseEntity<String> response = getWithSession(PROTECTED_PATH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(response)).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void activeVerifiedUser_returns200() {
        seedSessionFor(saveUser(true, false, false));

        ResponseEntity<String> response = getWithSession(PROTECTED_PATH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UserId saveUser(boolean verified, boolean suspended, boolean deleted) {
        UserId userId = userService.createUnverified(
                "gate-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
        User base = userService.lookup(userId);
        Instant now = base.createdAt();
        userRepository.save(new User(userId, base.email(), base.passwordHash(),
                verified, suspended, deleted, base.isAdmin(),
                now, now, deleted ? now : null));
        return userId;
    }

    private void seedSessionFor(UserId userId) {
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

    private ResponseEntity<String> getWithSession(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + RAW_SESSION_TOKEN);
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
    static class GatedEndpoints {

        @GetMapping("/__test/protected")
        String protectedEndpoint() {
            return "ok";
        }
    }
}
