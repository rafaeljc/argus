package io.github.rafaeljc.argus.eodpipeline.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.web.CsrfCookieFactory;
import io.github.rafaeljc.argus.auth.web.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import io.github.rafaeljc.argus.users.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, AdminEodPipelineControllerIT.TestStubsConfig.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminEodPipelineControllerIT {

    private static final String ENDPOINT = "/api/v1/admin/eod-pipeline/runs";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String CSRF_VALUE = "eod-pipeline-it-csrf-token";

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
    private SessionRepository sessionRepository;

    @Autowired
    private EodPipelineRunRepository runs;

    @Autowired
    private RecordingRunDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postRuns_validRequest_writesEodRunAuditRow() throws Exception {
        User admin = seedVerified("admin-audit1@example.com");

        ResponseEntity<String> response = post(admin, "{\"run_date\":\"2026-06-22\"}");

        String runId = json.readTree(response.getBody()).get("data").get("run_id").asString();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM admin_audit_log WHERE action = 'EOD_RUN' AND actor_id = ?", admin.id().value());
        assertThat(row.get("target_user_id")).isNull();
        assertThat(row.get("metadata").toString()).contains(runId).contains("2026-06-22");
    }

    @Test
    void postStep_prices_writesEodStepRerunAuditRow() throws Exception {
        User admin = seedVerified("admin-audit2@example.com");
        LocalDate runDate = LocalDate.of(2026, 7, 5);
        EodPipelineRun saved = runs.insert(failedRun(
                runDate, StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED));

        postStep(admin, saved.id(), "prices");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM admin_audit_log WHERE action = 'EOD_STEP_RERUN' AND actor_id = ?",
                admin.id().value());
        assertThat(row.get("target_user_id")).isNull();
        assertThat(row.get("metadata").toString())
                .contains(saved.id().value().toString())
                .contains("prices");
    }

    @Test
    void postRuns_validRequest_returns201WithLocationAndEnvelope() throws Exception {
        User admin = seedVerified("admin@example.com");

        ResponseEntity<String> response = post(admin, "{\"run_date\":\"2026-06-22\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).matches(".*/api/v1/admin/eod-pipeline/runs/[0-9a-fA-F-]{36}$");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("run_id").asString()).isNotBlank();
        assertThat(data.get("run_date").asString()).isEqualTo("2026-06-22");
        assertThat(data.get("trigger").asString()).isEqualTo("admin");
        assertThat(data.get("status").asString()).isEqualTo("in_progress");
        assertThat(data.get("step_symbols_status").asString()).isEqualTo("pending");
        assertThat(data.get("step_prices_status").asString()).isEqualTo("pending");
        assertThat(data.get("step_evaluate_status").asString()).isEqualTo("pending");
        assertThat(data.get("finished_at").isNull()).isTrue();
        assertThat(data.get("error_message").isNull()).isTrue();

        RunId insertedId = new RunId(UUID.fromString(data.get("run_id").asString()));
        assertThat(runs.findById(insertedId)).isPresent();
        assertThat(dispatcher.dispatched()).contains(insertedId);
    }

    @Test
    void postRuns_runAlreadyActiveForDate_returns409Conflict() throws Exception {
        User admin = seedVerified("admin2@example.com");
        LocalDate runDate = LocalDate.of(2026, 6, 23);
        runs.insert(pendingRun(runDate));

        ResponseEntity<String> response = post(admin, "{\"run_date\":\"2026-06-23\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("CONFLICT");
    }

    @Test
    void postRuns_noBody_defaultsToToday() throws Exception {
        User admin = seedVerified("admin3@example.com");
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

        ResponseEntity<String> response = post(admin, null);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("run_date").asString()).isEqualTo(today.toString());
    }

    @Test
    void postRuns_noSession_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>("{\"run_date\":\"2026-06-24\"}", headers),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void getRuns_authenticated_returnsPageWithEnvelope() throws Exception {
        User admin = seedVerified("admin4@example.com");
        runs.insert(runStartedAt(LocalDate.of(2026, 6, 25), Instant.parse("2026-06-25T21:30:00Z")));
        runs.insert(runStartedAt(LocalDate.of(2026, 6, 26), Instant.parse("2026-06-26T21:30:00Z")));

        ResponseEntity<String> response = get(admin, "");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("data")).hasSize(2);
        JsonNode meta = body.get("meta");
        assertThat(meta.get("total").asInt()).isEqualTo(2);
        assertThat(meta.get("page").asInt()).isEqualTo(1);
        assertThat(meta.get("per_page").asInt()).isEqualTo(50);
        assertThat(meta.get("total_pages").asInt()).isEqualTo(1);
        JsonNode links = body.get("links");
        assertThat(links.get("self").asString()).contains("page=1").contains("per_page=50");
        assertThat(links.get("last").asString()).contains("page=1");
        assertThat(links.get("next").isNull()).isTrue();
        assertThat(links.get("prev").isNull()).isTrue();
    }

    @Test
    void getRuns_multiplePages_slicesAndOrdersByStartedAtDescending() throws Exception {
        User admin = seedVerified("admin5@example.com");
        LocalDate d1 = LocalDate.of(2026, 6, 27);
        LocalDate d2 = LocalDate.of(2026, 6, 28);
        LocalDate d3 = LocalDate.of(2026, 6, 29);
        EodPipelineRun oldest = runs.insert(runStartedAt(d1, Instant.parse("2026-06-27T21:30:00Z")));
        EodPipelineRun middle = runs.insert(runStartedAt(d2, Instant.parse("2026-06-28T21:30:00Z")));
        EodPipelineRun newest = runs.insert(runStartedAt(d3, Instant.parse("2026-06-29T21:30:00Z")));

        ResponseEntity<String> firstPage = get(admin, "?page=1&per_page=2");
        ResponseEntity<String> secondPage = get(admin, "?page=2&per_page=2");

        JsonNode firstPageData = json.readTree(firstPage.getBody()).get("data");
        assertThat(firstPageData.get(0).get("run_id").asString()).isEqualTo(newest.id().value().toString());
        assertThat(firstPageData.get(1).get("run_id").asString()).isEqualTo(middle.id().value().toString());
        JsonNode secondPageBody = json.readTree(secondPage.getBody());
        JsonNode secondPageData = secondPageBody.get("data");
        assertThat(secondPageData).hasSize(1);
        assertThat(secondPageData.get(0).get("run_id").asString()).isEqualTo(oldest.id().value().toString());
        JsonNode links = secondPageBody.get("links");
        assertThat(links.get("prev").asString()).contains("page=1");
        assertThat(links.get("next").isNull()).isTrue();
    }

    @Test
    void getRun_existingId_returns200WithRunFields() throws Exception {
        User admin = seedVerified("admin6@example.com");
        LocalDate runDate = LocalDate.of(2026, 6, 30);
        EodPipelineRun saved = runs.insert(runStartedAt(runDate, Instant.parse("2026-06-30T21:30:00Z")));

        ResponseEntity<String> response = get(admin, "/" + saved.id().value());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("run_id").asString()).isEqualTo(saved.id().value().toString());
        assertThat(data.get("run_date").asString()).isEqualTo("2026-06-30");
        assertThat(data.get("status").asString()).isEqualTo("pending");
        assertThat(data.get("step_symbols_status").asString()).isEqualTo("pending");
    }

    @Test
    void getRun_unknownId_returns404() throws Exception {
        User admin = seedVerified("admin7@example.com");

        ResponseEntity<String> response = get(admin, "/" + UuidCreator.getTimeOrderedEpoch());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void getRuns_noSession_returns401() {
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void postStep_prices_returns200AndResetsPricesAndEvaluateAndDispatchesFromPrices() throws Exception {
        User admin = seedVerified("admin8@example.com");
        LocalDate runDate = LocalDate.of(2026, 7, 1);
        EodPipelineRun saved = runs.insert(failedRun(
                runDate, StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED));

        ResponseEntity<String> response = postStep(admin, saved.id(), "prices");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("run_id").asString()).isEqualTo(saved.id().value().toString());
        assertThat(data.get("step").asString()).isEqualTo("prices");
        assertThat(data.get("status").asString()).isEqualTo("in_progress");

        // The run is in_progress, but prices is queued rather than running: the worker marks it
        // in_progress when it claims it, which is what lets a competing rerun be rejected.
        EodPipelineRun updated = runs.findById(saved.id()).orElseThrow();
        assertThat(updated.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(updated.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(updated.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(updated.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(dispatcher.dispatchedFrom())
                .contains(new RecordingRunDispatcher.DispatchedFrom(saved.id(), PipelineStep.PRICES));
    }

    @Test
    void postStep_namedStepAlreadyInProgress_returns409Conflict() throws Exception {
        User admin = seedVerified("admin9@example.com");
        LocalDate runDate = LocalDate.of(2026, 7, 2);
        EodPipelineRun saved = runs.insert(inProgressRun(
                runDate, StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING));

        ResponseEntity<String> response = postStep(admin, saved.id(), "prices");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("CONFLICT");
    }

    @Test
    void postStep_differentStepInProgress_returns409Conflict() throws Exception {
        User admin = seedVerified("admin10@example.com");
        LocalDate runDate = LocalDate.of(2026, 7, 3);
        EodPipelineRun saved = runs.insert(inProgressRun(
                runDate, StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING));

        ResponseEntity<String> response = postStep(admin, saved.id(), "evaluate");

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("CONFLICT");
    }

    @Test
    void postStep_unknownRunId_returns404() throws Exception {
        User admin = seedVerified("admin11@example.com");

        ResponseEntity<String> response = postStep(admin, new RunId(UuidCreator.getTimeOrderedEpoch()), "prices");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void postStep_unknownStep_returns404() throws Exception {
        User admin = seedVerified("admin12@example.com");
        EodPipelineRun saved = runs.insert(pendingRun(LocalDate.of(2026, 7, 4)));

        ResponseEntity<String> response = postStep(admin, saved.id(), "bogus");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void postStep_noSession_returns401() {
        RunId runId = new RunId(UuidCreator.getTimeOrderedEpoch());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + runId.value() + "/steps/prices",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> postStep(User authenticatedAs, RunId runId, String step) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        headers.add("X-CSRF-Token", CSRF_VALUE);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + "/" + runId.value() + "/steps/" + step,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
    }

    private static EodPipelineRun failedRun(
            LocalDate runDate, StepStatus symbols, StepStatus prices, StepStatus evaluate) {
        Instant now = Instant.now();
        return new EodPipelineRun(
                new RunId(UuidCreator.getTimeOrderedEpoch()), runDate, Trigger.CRON, RunStatus.FAILED, now,
                now.plusSeconds(60), symbols, prices, evaluate, "boom");
    }

    private static EodPipelineRun inProgressRun(
            LocalDate runDate, StepStatus symbols, StepStatus prices, StepStatus evaluate) {
        Instant now = Instant.now();
        return new EodPipelineRun(
                new RunId(UuidCreator.getTimeOrderedEpoch()), runDate, Trigger.CRON, RunStatus.IN_PROGRESS, now,
                null, symbols, prices, evaluate, null);
    }

    private ResponseEntity<String> get(User authenticatedAs, String pathAndQuery) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE, SessionCookieFactory.COOKIE_NAME + "=" + sessionToken);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT + pathAndQuery,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private static EodPipelineRun runStartedAt(LocalDate runDate, Instant startedAt) {
        return new EodPipelineRun(
                new RunId(UuidCreator.getTimeOrderedEpoch()), runDate, Trigger.CRON, RunStatus.PENDING, startedAt,
                null, StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    private ResponseEntity<String> post(User authenticatedAs, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        String sessionToken = seedSession(authenticatedAs);
        headers.add(HttpHeaders.COOKIE,
                SessionCookieFactory.COOKIE_NAME + "=" + sessionToken
                        + "; " + CsrfCookieFactory.COOKIE_NAME + "=" + CSRF_VALUE);
        headers.add("X-CSRF-Token", CSRF_VALUE);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private User seedVerified(String email) {
        User u = userService.createUnverified(email, PASSWORD);
        User verified = userService.markVerified(u.id());
        Instant now = verified.createdAt();
        return userRepository.save(new User(verified.id(), verified.email(), verified.passwordHash(),
                verified.isVerified(), verified.isSuspended(), verified.isDeleted(), true,
                now, now, null));
    }

    private String seedSession(User user) {
        String token = "eod-pipeline-it-session-" + UuidCreator.getTimeOrderedEpoch();
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

    private static EodPipelineRun pendingRun(LocalDate runDate) {
        Instant now = Instant.now();
        return new EodPipelineRun(
                new RunId(UuidCreator.getTimeOrderedEpoch()), runDate, Trigger.CRON, RunStatus.PENDING, now, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @TestConfiguration
    static class TestStubsConfig {
        @Bean
        @Primary
        RecordingRunDispatcher recordingRunDispatcher() {
            return new RecordingRunDispatcher();
        }
    }

    // Stands in for ExecutorRunDispatcher: prevents the real background pipeline (which would hit
    // vendor gateways and race the next test's TRUNCATE cleanup) from running during this HTTP-slice IT.
    static final class RecordingRunDispatcher implements RunDispatcher {
        private final CopyOnWriteArrayList<RunId> dispatched = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<DispatchedFrom> dispatchedFrom = new CopyOnWriteArrayList<>();

        @Override
        public void dispatch(RunId id) {
            dispatched.add(id);
        }

        @Override
        public void dispatchFrom(RunId id, PipelineStep entryStep) {
            dispatchedFrom.add(new DispatchedFrom(id, entryStep));
        }

        CopyOnWriteArrayList<RunId> dispatched() {
            return dispatched;
        }

        CopyOnWriteArrayList<DispatchedFrom> dispatchedFrom() {
            return dispatchedFrom;
        }

        record DispatchedFrom(RunId runId, PipelineStep entryStep) {
        }
    }
}
