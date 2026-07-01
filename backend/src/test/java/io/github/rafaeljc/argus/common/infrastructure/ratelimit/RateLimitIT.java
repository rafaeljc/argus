package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.CsrfCookieFactory;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import io.github.rafaeljc.argus.users.web.TestCurrentUserIdConfig;
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

@Import({PostgresContainer.class, TestCurrentUserIdConfig.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    private static final String ENDPOINT = "/api/v1/account/me";
    private static final String LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RESET_HEADER = "X-RateLimit-Reset";
    private static final String CSRF_VALUE = "ratelimit-it-csrf-token";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserService userService;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    void authenticatedRead_emitsRateLimitHeadersAndDecrementsRemaining() {
        User user = userService.createUnverified("ratelimit-it@example.com", "correct horse battery staple");
        String sessionToken = seedSession(user);

        ResponseEntity<String> first = get(user, sessionToken);
        ResponseEntity<String> second = get(user, sessionToken);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);

        // RL.read bucket: capacity 300, user-keyed, greedy refill of 300 tokens per minute
        // (~5 tokens/s). Two consecutive reads from the same user share one bucket; the
        // invariant asserted is that the second call sees no more tokens than the first —
        // an exact decrement is not asserted because wall-clock jitter between requests can
        // refill a fractional token during Spring/Tomcat processing.
        assertThat(first.getHeaders().getFirst(LIMIT_HEADER)).isEqualTo("300");
        assertThat(first.getHeaders().getFirst(RESET_HEADER)).isNotNull();
        long firstRemaining = Long.parseLong(first.getHeaders().getFirst(REMAINING_HEADER));
        assertThat(firstRemaining).isEqualTo(299L);

        assertThat(second.getHeaders().getFirst(LIMIT_HEADER)).isEqualTo("300");
        assertThat(second.getHeaders().getFirst(RESET_HEADER)).isNotNull();
        long secondRemaining = Long.parseLong(second.getHeaders().getFirst(REMAINING_HEADER));
        assertThat(secondRemaining).isLessThanOrEqualTo(firstRemaining);
        assertThat(secondRemaining).isLessThan(300L);
    }

    private ResponseEntity<String> get(User authenticatedAs, String sessionToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TestCurrentUserIdConfig.HEADER, authenticatedAs.id().value().toString());
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private String seedSession(User user) {
        String token = "ratelimit-it-session-" + UuidCreator.getTimeOrderedEpoch();
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
