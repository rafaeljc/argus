package io.github.rafaeljc.argus.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, RedisContainer.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserControllerIT {

    private static final String ENDPOINT = "/api/v1/admin/users";

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
    private FindByIndexNameSessionRepository<?> sessions;

    @Autowired
    private JdbcTemplate jdbc;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void suspend_activeUser_returns200SuspendsAndPurgesSessions() throws Exception {
        TestSession admin = seedAdmin("admin-suspend1@example.com");
        TestSession target = seedPlain("target-suspend1@example.com");

        ResponseEntity<String> response = action(admin, target.userId(), "suspend", "{\"reason\":\"abuse\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("id").asString()).isEqualTo(target.userId().value().toString());
        assertThat(data.get("is_suspended").asBoolean()).isTrue();
        assertThat(sessionsFor(target)).isEmpty();
    }

    @Test
    void suspend_repeatedCall_writesExactlyOneAuditRow() throws Exception {
        TestSession admin = seedAdmin("admin-suspend2@example.com");
        TestSession target = seedPlain("target-suspend2@example.com");

        action(admin, target.userId(), "suspend", "{\"reason\":\"abuse\"}");
        ResponseEntity<String> second = action(admin, target.userId(), "suspend", "{\"reason\":\"abuse\"}");

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(auditRowCount(target.userId(), "SUSPEND")).isEqualTo(1);
    }

    @Test
    void suspend_reason_landsInAuditMetadata() throws Exception {
        TestSession admin = seedAdmin("admin-suspend3@example.com");
        TestSession target = seedPlain("target-suspend3@example.com");

        action(admin, target.userId(), "suspend", "{\"reason\":\"repeated abuse reports\"}");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT metadata FROM admin_audit_log WHERE target_user_id = ? AND action = 'SUSPEND'",
                target.userId().value());
        assertThat(row.get("metadata").toString()).contains("repeated abuse reports");
    }

    @Test
    void suspend_unknownUser_returns404() throws Exception {
        TestSession admin = seedAdmin("admin-suspend4@example.com");

        ResponseEntity<String> response =
                action(admin, new UserId(UuidCreator.getTimeOrderedEpoch()), "suspend", null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void suspend_nonAdminActor_returns403() throws Exception {
        TestSession nonAdmin = seedPlain("actor-suspend5@example.com");
        TestSession target = seedPlain("target-suspend5@example.com");

        ResponseEntity<String> response = action(nonAdmin, target.userId(), "suspend", null);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void unsuspend_suspendedUser_returns200AndDoesNotPurgeSessions() throws Exception {
        TestSession admin = seedAdmin("admin-unsuspend1@example.com");
        TestSession target = seedPlain("target-unsuspend1@example.com");
        // Direct repository flip (not the /suspend endpoint): the endpoint would itself purge
        // sessions, defeating the point of this test.
        suspend(target.userId());

        ResponseEntity<String> response = action(admin, target.userId(), "unsuspend", "{\"reason\":\"appeal\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("is_suspended").asBoolean()).isFalse();
        assertThat(sessionsFor(target)).isNotEmpty();
    }

    @Test
    void unsuspend_repeatedCall_writesExactlyOneAuditRow() throws Exception {
        TestSession admin = seedAdmin("admin-unsuspend2@example.com");
        TestSession target = seedPlain("target-unsuspend2@example.com");
        suspend(target.userId());

        action(admin, target.userId(), "unsuspend", "{\"reason\":\"appeal\"}");
        ResponseEntity<String> second = action(admin, target.userId(), "unsuspend", "{\"reason\":\"appeal\"}");

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(auditRowCount(target.userId(), "UNSUSPEND")).isEqualTo(1);
    }

    @Test
    void delete_activeUser_returns200DeletesAndPurgesSessions() throws Exception {
        TestSession admin = seedAdmin("admin-delete1@example.com");
        TestSession target = seedPlain("target-delete1@example.com");

        ResponseEntity<String> response = action(admin, target.userId(), "delete", "{\"reason\":\"policy\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("is_deleted").asBoolean()).isTrue();
        assertThat(data.get("deleted_at").isNull()).isFalse();
        assertThat(sessionsFor(target)).isEmpty();
    }

    @Test
    void delete_repeatedCall_writesExactlyOneAuditRowAndDoesNotRestampDeletedAt() throws Exception {
        TestSession admin = seedAdmin("admin-delete2@example.com");
        TestSession target = seedPlain("target-delete2@example.com");

        ResponseEntity<String> first = action(admin, target.userId(), "delete", "{\"reason\":\"policy\"}");
        ResponseEntity<String> second = action(admin, target.userId(), "delete", "{\"reason\":\"policy\"}");

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(auditRowCount(target.userId(), "DELETE")).isEqualTo(1);
        String firstDeletedAt = json.readTree(first.getBody()).get("data").get("deleted_at").asString();
        String secondDeletedAt = json.readTree(second.getBody()).get("data").get("deleted_at").asString();
        assertThat(secondDeletedAt).isEqualTo(firstDeletedAt);
    }

    @Test
    void delete_thenReusingTargetsOriginalSession_isRejectedByAccountStateGate() throws Exception {
        TestSession admin = seedAdmin("admin-delete3@example.com");
        TestSession target = seedPlain("target-delete3@example.com");

        action(admin, target.userId(), "delete", null);

        // Soft-delete invalidates the target's Redis sessions in the same transaction; the
        // pre-delete cookie the browser is still holding no longer resolves to anyone.
        ResponseEntity<String> gated = http.exchange(
                "http://localhost:" + port + "/api/v1/portfolio",
                HttpMethod.GET,
                new HttpEntity<>(target.headers()),
                String.class);
        assertThat(gated.getStatusCode().value()).isEqualTo(401);
    }

    private int auditRowCount(UserId targetId, String action) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_log WHERE target_user_id = ? AND action = ?",
                Integer.class, targetId.value(), action);
    }

    private ResponseEntity<String> action(TestSession authenticatedAs, UserId targetId, String actionName,
                                          String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(authenticatedAs.headers());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + targetId.value() + "/" + actionName,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    @Test
    void search_noFilters_returnsAllUsersWithEnvelope() throws Exception {
        TestSession admin = seedAdmin("admin1@example.com");
        TestSession other = seedPlain("other1@example.com");

        ResponseEntity<String> response = search(admin, "", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(idsOf(body)).contains(admin.userId().value().toString(), other.userId().value().toString());
    }

    @Test
    void search_isSuspendedTrue_returnsOnlySuspended() throws Exception {
        TestSession admin = seedAdmin("admin2@example.com");
        TestSession suspended = seedPlain("suspended2@example.com");
        suspend(suspended.userId());
        seedPlain("notsuspended2@example.com");

        ResponseEntity<String> response = search(admin, "?is_suspended=true", null);

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(suspended.userId().value().toString());
    }

    @Test
    void search_isDeletedTrue_returnsOnlyDeleted() throws Exception {
        TestSession admin = seedAdmin("admin3@example.com");
        TestSession deleted = seedPlain("deleted3@example.com");
        userService.softDelete(deleted.userId(), TestLogin.PASSWORD);
        seedPlain("notdeleted3@example.com");

        ResponseEntity<String> response = search(admin, "?is_deleted=true", null);

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(deleted.userId().value().toString());
    }

    @Test
    void search_isVerifiedFalse_returnsOnlyUnverified() throws Exception {
        TestSession admin = seedAdmin("admin4@example.com");
        User unverified = userService.createUnverified("unverified4@example.com", TestLogin.PASSWORD);
        seedPlain("verified4@example.com");

        ResponseEntity<String> response = search(admin, "?is_verified=false", null);

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).contains(unverified.id().value().toString());
    }

    @Test
    void search_combinedFilters_appliesAllPredicates() throws Exception {
        TestSession match = seedPlain("acme5@example.com");
        suspend(match.userId());
        TestSession wrongEmail = seedPlain("other5@example.com");
        suspend(wrongEmail.userId());
        seedPlain("acme5b@example.com");
        TestSession admin = seedAdmin("admin5@example.com");

        ResponseEntity<String> response = search(admin, "?is_suspended=true", "{\"email_contains\":\"acme5\"}");

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(match.userId().value().toString());
    }

    @Test
    void search_emailContainsFragment_isCaseInsensitivePartialMatch() throws Exception {
        TestSession admin = seedAdmin("admin6@example.com");
        TestSession target = seedPlain("jane.doe@ACME6.com");
        seedPlain("someone-else6@example.com");

        ResponseEntity<String> response = search(admin, "", "{\"email_contains\":\"acme6\"}");

        JsonNode body = json.readTree(response.getBody());
        assertThat(idsOf(body)).containsExactly(target.userId().value().toString());
    }

    @Test
    void search_pagination_populatesMetaAndPreservesFiltersInLinks() throws Exception {
        seedPlain("first7@example.com");
        seedPlain("second7@example.com");
        seedPlain("third7@example.com");
        TestSession admin = seedAdmin("admin7@example.com");

        ResponseEntity<String> response = search(admin, "?is_suspended=false&page=1&per_page=2", null);

        JsonNode body = json.readTree(response.getBody());
        JsonNode meta = body.get("meta");
        assertThat(meta.get("page").asInt()).isEqualTo(1);
        assertThat(meta.get("per_page").asInt()).isEqualTo(2);
        assertThat(body.get("data")).hasSize(2);
        JsonNode links = body.get("links");
        assertThat(links.get("next").asString()).contains("is_suspended=false").contains("page=2");
        assertThat(links.get("self").asString()).contains("is_suspended=false");
    }

    @Test
    void getUser_existingDeletedUser_returns200() throws Exception {
        TestSession admin = seedAdmin("admin9@example.com");
        TestSession deleted = seedPlain("deleted9@example.com");
        userService.softDelete(deleted.userId(), TestLogin.PASSWORD);

        ResponseEntity<String> response = get(admin, "/" + deleted.userId().value());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("id").asString()).isEqualTo(deleted.userId().value().toString());
        assertThat(data.get("is_deleted").asBoolean()).isTrue();
    }

    @Test
    void getUser_unknownId_returns404() throws Exception {
        TestSession admin = seedAdmin("admin10@example.com");

        ResponseEntity<String> response = get(admin, "/" + UuidCreator.getTimeOrderedEpoch());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
    }

    private static List<String> idsOf(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.get("data").forEach(node -> ids.add(node.get("id").asString()));
        return ids;
    }

    private ResponseEntity<String> search(TestSession authenticatedAs, String query, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(authenticatedAs.headers());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + query,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private ResponseEntity<String> get(TestSession authenticatedAs, String pathAndQuery) {
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + pathAndQuery,
                HttpMethod.GET,
                new HttpEntity<>(authenticatedAs.headers()),
                String.class);
    }

    private TestSession seedAdmin(String email) {
        return testLogin.login(email, true);
    }

    private TestSession seedPlain(String email) {
        return testLogin.login(email);
    }

    private void suspend(UserId id) {
        userRepository.save(suspendedCopy(userRepository.findById(id).orElseThrow()));
    }

    private static User suspendedCopy(User user) {
        return new User(user.id(), user.email(), user.passwordHash(), user.isVerified(), true,
                user.isDeleted(), user.isAdmin(), user.createdAt(), user.updatedAt(), user.deletedAt());
    }

    private Map<String, ?> sessionsFor(TestSession session) {
        return sessions.findByPrincipalName(session.userId().value().toString());
    }
}
