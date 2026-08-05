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
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
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
    private SessionRepository sessionRepository;

    @Autowired
    private EodPipelineRunRepository runs;

    @Autowired
    private RecordingRunDispatcher dispatcher;

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
        return userService.markVerified(u.id());
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

        @Override
        public void dispatch(RunId id) {
            dispatched.add(id);
        }

        CopyOnWriteArrayList<RunId> dispatched() {
            return dispatched;
        }
    }
}
