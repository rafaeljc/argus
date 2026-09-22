package io.github.rafaeljc.argus.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.admin.application.port.AuditLogRepository;
import io.github.rafaeljc.argus.admin.domain.AdminAction;
import io.github.rafaeljc.argus.admin.domain.AuditLogEntry;
import io.github.rafaeljc.argus.admin.domain.AuditMetadata;
import io.github.rafaeljc.argus.common.domain.AuditEntryId;
import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
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
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuditLogControllerIT {

    private static final String ENDPOINT = "/api/v1/admin/audit-log";
    private static final Instant CREATED_AT = Instant.parse("2026-06-15T21:00:00Z");

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
    private AuditLogRepository auditLogRepository;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void list_asAdmin_returnsEnvelopeWithEntry() throws Exception {
        TestSession admin = testLogin.login("admin1@example.com", true);
        TestSession target = testLogin.login("target1@example.com");
        AuditEntryId entryId = new AuditEntryId(UuidCreator.getTimeOrderedEpoch());
        auditLogRepository.insert(new AuditLogEntry(entryId, admin.userId(), AdminAction.SUSPEND, target.userId(),
                new AuditMetadata.UserAction("abuse"), CREATED_AT));

        ResponseEntity<String> response = list(admin, "");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).hasSize(1);
        JsonNode entry = body.get("data").get(0);
        assertThat(entry.get("id").asString()).isEqualTo(entryId.value().toString());
        assertThat(entry.get("actor_id").asString()).isEqualTo(admin.userId().value().toString());
        assertThat(entry.get("action").asString()).isEqualTo("SUSPEND");
        assertThat(entry.get("target_user_id").asString()).isEqualTo(target.userId().value().toString());
        assertThat(entry.get("metadata").isObject()).isTrue();
        assertThat(entry.get("metadata").get("reason").asString()).isEqualTo("abuse");
        assertThat(entry.get("created_at").asString()).isNotBlank();
    }

    @Test
    void list_asNonAdmin_returns403() {
        TestSession plain = testLogin.login("plain1@example.com");

        ResponseEntity<String> response = list(plain, "");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void list_unauthenticated_returns401() {
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void list_invalidAction_returns400() {
        TestSession admin = testLogin.login("admin2@example.com", true);

        ResponseEntity<String> response = list(admin, "?action=not_a_real_action");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void list_perPageOverMax_returns422() {
        TestSession admin = testLogin.login("admin3@example.com", true);

        ResponseEntity<String> response = list(admin, "?per_page=201");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void list_pagination_linksNextIsNullOnLastPage() throws Exception {
        TestSession admin = testLogin.login("admin4@example.com", true);
        TestSession target = testLogin.login("target4@example.com");
        auditLogRepository.insert(new AuditLogEntry(new AuditEntryId(UuidCreator.getTimeOrderedEpoch()),
                admin.userId(), AdminAction.SUSPEND, target.userId(), null, CREATED_AT));

        ResponseEntity<String> response = list(admin, "?page=1&per_page=50");

        JsonNode links = json.readTree(response.getBody()).get("links");
        assertThat(links.get("next").isNull()).isTrue();
    }

    private ResponseEntity<String> list(TestSession authenticatedAs, String query) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + query,
                HttpMethod.GET,
                new HttpEntity<>(authenticatedAs.headers()),
                String.class);
    }
}
