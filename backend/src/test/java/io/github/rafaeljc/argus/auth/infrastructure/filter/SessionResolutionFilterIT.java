package io.github.rafaeljc.argus.auth.infrastructure.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.infrastructure.security.AuthenticatedSession;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionAuthenticationToken;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({PostgresContainer.class, SessionResolutionFilterIT.WhoamiEndpoint.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionResolutionFilterIT {

    private static final String RAW_TOKEN = "raw-session-token-it";
    private static final String WHOAMI_PATH = "/api/v1/__test/whoami";

    @LocalServerPort
    private int port;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TestRestTemplate http;

    @Test
    void validSession_setsPrincipal_touchesDb_andReemitsRollingCookie() {
        UserId userId = newUser();
        SessionId sessionId = newSessionId();
        Instant createdAt = Instant.parse("2026-06-22T12:00:00Z");
        Instant farFutureExpiry = createdAt.plus(Duration.ofDays(365));
        Session seeded = save(sessionId, userId, "10.0.0.1", "old-agent", createdAt, farFutureExpiry);

        ResponseEntity<String> response = exchangeWithCookie(WHOAMI_PATH, RAW_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userId.value() + "/" + sessionId.value());
        // Confirms the rolling cookie reached the wire carrying the same token;
        // SessionResolutionFilterTest pins the Max-Age / SameSite / HttpOnly / Secure attrs.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith(SessionCookieFactory.COOKIE_NAME + "=" + RAW_TOKEN + ";"));

        Session refreshed = sessionRepository.findByTokenHash(sha256Hex(RAW_TOKEN)).orElseThrow();
        assertThat(refreshed.lastActivityAt()).isNotEqualTo(seeded.lastActivityAt());
        assertThat(refreshed.expiresAt()).isNotEqualTo(seeded.expiresAt());
        assertThat(refreshed.ipAddress()).isNotNull();
        assertThat(refreshed.userAgent()).isEqualTo("IT-Agent");
    }

    @Test
    void expiredSession_deletesRow_clearsCookie_andDoesNotAuthenticate() {
        UserId userId = newUser();
        SessionId sessionId = newSessionId();
        Instant ancientCreated = Instant.parse("2026-06-22T12:00:00Z");
        save(sessionId, userId, null, null, ancientCreated, ancientCreated.plusSeconds(1));

        ResponseEntity<String> response = exchangeWithCookie(WHOAMI_PATH, RAW_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("anonymous");
        // Confirms the cleared cookie reached the wire with empty value;
        // SessionResolutionFilterTest already pins Max-Age=0 / SameSite / HttpOnly / Secure
        // on the Cookie model.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith(SessionCookieFactory.COOKIE_NAME + "=;"));
        assertThat(sessionRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).isEmpty();
    }

    @Test
    void noCookie_doesNotAuthenticate_andEmitsNoSessionCookie() {
        ResponseEntity<String> response = http.getForEntity(url(WHOAMI_PATH), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("anonymous");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .satisfiesAnyOf(
                        list -> assertThat(list).isNullOrEmpty(),
                        list -> assertThat(list).noneMatch(h -> h.startsWith(SessionCookieFactory.COOKIE_NAME + "=")));
    }

    @Test
    void unknownToken_doesNotAuthenticate() {
        ResponseEntity<String> response = exchangeWithCookie(WHOAMI_PATH, "not-a-real-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("anonymous");
    }

    private Session save(SessionId sessionId, UserId userId, String ip, String ua,
                         Instant createdAt, Instant expiresAt) {
        return sessionRepository.save(new Session(sessionId, userId, sha256Hex(RAW_TOKEN),
                ip, ua, createdAt, expiresAt, createdAt));
    }

    private ResponseEntity<String> exchangeWithCookie(String path, String sessionTokenValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionTokenValue);
        headers.add(HttpHeaders.USER_AGENT, "IT-Agent");
        return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private UserId newUser() {
        return userService.createUnverified(
                "user-" + UuidCreator.getTimeOrderedEpoch() + "@example.com",
                "correct horse battery staple").id();
    }

    private static SessionId newSessionId() {
        return new SessionId(UuidCreator.getTimeOrderedEpoch());
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
    static class WhoamiEndpoint {

        @GetMapping("/__test/whoami")
        String whoami() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof SessionAuthenticationToken token)) {
                return "anonymous";
            }
            AuthenticatedSession principal = (AuthenticatedSession) token.getPrincipal();
            return principal.userId().value() + "/" + principal.sessionId().value();
        }
    }
}
