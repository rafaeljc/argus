package io.github.rafaeljc.argus.admin.web;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthorizationIT {

    private static final String ENDPOINT = "/api/v1/admin/eod-pipeline/runs";

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

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void get_noSession_returns401() {
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void get_authenticatedNonAdmin_returns403ForbiddenEnvelope() throws Exception {
        TestSession session = testLogin.login("non-admin@example.com");

        ResponseEntity<String> response = get(session);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void get_authenticatedAdmin_isNotForbidden() {
        TestSession session = testLogin.login("admin@example.com", true);

        ResponseEntity<String> response = get(session);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> get(TestSession session) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(session.headers()),
                String.class);
    }
}
