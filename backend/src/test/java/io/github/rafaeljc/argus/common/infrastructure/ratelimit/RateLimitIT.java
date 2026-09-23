package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    private static final String ENDPOINT = "/api/v1/account/me";
    private static final String LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RESET_HEADER = "X-RateLimit-Reset";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

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
    void authenticatedRead_emitsRateLimitHeadersAndDecrementsRemaining() {
        TestSession session = testLogin.login("ratelimit-it@example.com");

        ResponseEntity<String> first = get(session);
        ResponseEntity<String> second = get(session);

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

    private ResponseEntity<String> get(TestSession session) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(session.headers()),
                String.class);
    }
}
