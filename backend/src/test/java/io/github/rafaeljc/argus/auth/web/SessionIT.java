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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionIT {

    private static final String ACCOUNT_ME_PATH = "/api/v1/account/me";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FindByIndexNameSessionRepository<?> sessions;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void anonymousRequest_createsNoSession() {
        ResponseEntity<String> response = http.exchange(
                url(ACCOUNT_ME_PATH), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .satisfiesAnyOf(
                        list -> assertThat(list).isNullOrEmpty(),
                        list -> assertThat(list).noneMatch(h -> h.startsWith("argus_session=")));
    }

    @Test
    void authenticatedRequest_refreshesRollingWindow() throws InterruptedException {
        TestSession session = testLogin.login("session-rolling@example.com");
        Session before = onlySession(session);

        // Coarse enough to move past clock resolution on any CI host without slowing the suite.
        Thread.sleep(1100);
        ResponseEntity<String> response = http.exchange(
                url(ACCOUNT_ME_PATH), HttpMethod.GET, new HttpEntity<>(session.headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Session after = onlySession(session);
        assertThat(after.getLastAccessedTime()).isAfter(before.getLastAccessedTime());
    }

    @Test
    void unknownSessionCookie_doesNotAuthenticate() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "argus_session=not-a-real-session-id");

        ResponseEntity<String> response = http.exchange(
                url(ACCOUNT_ME_PATH), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Session onlySession(TestSession session) {
        return sessions.findByPrincipalName(session.userId().value().toString())
                .values().iterator().next();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
