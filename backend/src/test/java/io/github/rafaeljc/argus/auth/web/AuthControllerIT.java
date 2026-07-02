package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Every HTTP test in this class shares one JVM-local bucket store keyed by the localhost IP, so
// the production 5/h signup budget would exhaust part-way through a run. Bumping the buckets here
// only relaxes what needs relaxing to exercise the endpoints; a dedicated rate-limit IT stays
// responsible for enforcing the production numbers.
@TestPropertySource(properties = {
        "argus.rate-limit.buckets.[RL.auth.signup].capacity=1000",
        "argus.rate-limit.buckets.[RL.auth.signup].refill-tokens=1000",
        "argus.rate-limit.buckets.[RL.auth.signup].refill-duration=PT1M",
        "argus.rate-limit.buckets.[RL.auth.login].capacity=1000",
        "argus.rate-limit.buckets.[RL.auth.login].refill-tokens=1000",
        "argus.rate-limit.buckets.[RL.auth.login].refill-duration=PT1M",
        "argus.rate-limit.buckets.[RL.auth.reset].capacity=1000",
        "argus.rate-limit.buckets.[RL.auth.reset].refill-tokens=1000",
        "argus.rate-limit.buckets.[RL.auth.reset].refill-duration=PT1M",
        "argus.rate-limit.buckets.[RL.unauth.global].capacity=1000",
        "argus.rate-limit.buckets.[RL.unauth.global].refill-tokens=1000",
        "argus.rate-limit.buckets.[RL.unauth.global].refill-duration=PT1M"
})
class AuthControllerIT {

    private static final String ENDPOINT = "/api/v1/auth/signup";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";
    private static final String STATUS_ENDPOINT = "/api/v1/auth/status";
    private static final String VERIFY_EMAIL_ENDPOINT = "/api/v1/auth/verify-email";
    private static final String PASSWORD_RESET_REQUEST_ENDPOINT = "/api/v1/auth/password-reset-requests";
    private static final String PASSWORD_RESET_COMPLETE_ENDPOINT = "/api/v1/auth/password-resets";
    private static final String VALID_PASSWORD = "correct horse battery staple";
    private static final String NEW_PASSWORD = "shiny new passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postSignup_validRequest_returns201WithLocationAndUserIdAndSeedsOutbox() {
        String email = "alice@example.com";

        ResponseEntity<String> response = post(body(email, VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/account/me");

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.propertyNames()).containsExactlyInAnyOrder("user_id", "verification_sent");
        assertThat(data.get("verification_sent").asBoolean()).isTrue();
        UUID userId = UUID.fromString(data.get("user_id").asString());

        Map<String, Object> userRow = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", userId);
        assertThat(userRow.get("email")).isEqualTo(email);
        assertThat(userRow.get("is_verified")).isEqualTo(false);
        assertThat(userRow.get("is_suspended")).isEqualTo(false);
        assertThat(userRow.get("is_deleted")).isEqualTo(false);

        Map<String, Object> verificationRow = jdbc.queryForMap(
                "SELECT * FROM email_verifications WHERE user_id = ?", userId);
        assertThat(verificationRow.get("token_hash")).asString().matches("[0-9a-f]{64}");
        assertThat(verificationRow.get("verified_at")).isNull();

        Map<String, Object> outboxRow = jdbc.queryForMap(
                "SELECT event_type, idempotence_key, payload::text AS payload "
                        + "FROM outbox WHERE aggregate_id = ?",
                userId);
        assertThat(outboxRow.get("event_type")).isEqualTo("email.verification");
        assertThat(outboxRow.get("idempotence_key")).asString().startsWith("email.verification:");
        JsonNode payload = json.readTree((String) outboxRow.get("payload"));
        assertThat(payload.get("user_id").asString()).isEqualTo(userId.toString());
        assertThat(payload.get("email").asString()).isEqualTo(email);
        assertThat(payload.get("token").asString()).matches("[A-Za-z0-9_-]+");
        assertThat(payload.get("expires_at").asString()).isNotEmpty();
        // The token in the outbox is the plain form (goes into the email link); DB stores only the hash.
        assertThat(payload.get("token").asString()).isNotEqualTo(verificationRow.get("token_hash"));
    }

    @Test
    void postSignup_malformedEmail_returns422ValidationErrorWithEmailField() {
        ResponseEntity<String> response = post(body("not-an-email", VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(fieldNames(error.get("details"))).contains("email");
    }

    @Test
    void postSignup_shortPassword_returns422ValidationErrorWithPasswordField() {
        ResponseEntity<String> response = post(body("bob@example.com", "short"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(fieldNames(error.get("details"))).contains("password");
    }

    @Test
    void postSignup_duplicateEmail_returns422ValidationErrorWithAlreadyTakenCode() {
        String email = "carol@example.com";
        userService.createUnverified(email, VALID_PASSWORD);

        ResponseEntity<String> response = post(body(email, VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        JsonNode error = json.readTree(response.getBody()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        JsonNode details = error.get("details");
        assertThat(details).hasSize(1);
        assertThat(details.get(0).get("field").asString()).isEqualTo("email");
        assertThat(details.get(0).get("code").asString()).isEqualTo("already_taken");
    }

    @Test
    void postSignup_emailWithMixedCase_persistsLowercasedForm() {
        // The User domain lowercases on construction so lookups and the unique index treat
        // "Dave@Example.COM" and "dave@example.com" as the same address.
        ResponseEntity<String> response = post(body("Dave@Example.COM", VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        UUID userId = UUID.fromString(
                json.readTree(response.getBody()).get("data").get("user_id").asString());
        String storedEmail = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, userId);
        assertThat(storedEmail).isEqualTo("dave@example.com");
    }

    // --- POST /auth/login ----------------------------------------------------------------------

    @Test
    void postLogin_validCredentials_returns200SetsBothCookiesAndPersistsSession() {
        String email = "login-valid@example.com";
        UUID userId = createVerifiedUser(email);

        ResponseEntity<String> response = postLogin(loginBody(email, VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("user_id").asString()).isEqualTo(userId.toString());
        assertThat(data.get("expires_at").asString()).isNotEmpty();

        Map<String, String> cookies = setCookiesByName(response);
        assertThat(cookies).containsKeys("argus_session", "argus_csrf");
        assertThat(cookies.get("argus_session")).isNotBlank();
        assertThat(cookies.get("argus_csrf")).isNotBlank();

        Integer sessionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sessions WHERE user_id = ?", Integer.class, userId);
        assertThat(sessionCount).isEqualTo(1);
    }

    @Test
    void postLogin_unknownEmail_returns401Unauthorized() {
        ResponseEntity<String> response = postLogin(loginBody("no-such-user@example.com", VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void postLogin_wrongPassword_returns401Unauthorized() {
        String email = "login-wrongpw@example.com";
        createVerifiedUser(email);

        ResponseEntity<String> response = postLogin(loginBody(email, "not-the-password"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void postLogin_unverifiedUser_returns401ToPreventEnumeration() {
        String email = "login-unverified@example.com";
        userService.createUnverified(email, VALID_PASSWORD);

        ResponseEntity<String> response = postLogin(loginBody(email, VALID_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("UNAUTHORIZED");
    }

    @Test
    void postLogin_missingPassword_returns422ValidationError() {
        ResponseEntity<String> response = postLogin("{\"email\":\"login-blank@example.com\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("VALIDATION_ERROR");
    }

    // --- POST /auth/logout ---------------------------------------------------------------------

    @Test
    void postLogout_validSession_returns204ClearsCookiesAndDeletesSessionRow() {
        String email = "logout-happy@example.com";
        final UUID userId = createVerifiedUser(email);
        Session session = login(email);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE,
                "argus_session=" + session.sessionCookie + "; argus_csrf=" + session.csrfCookie);
        headers.add("X-CSRF-Token", session.csrfCookie);
        ResponseEntity<String> response = http.exchange(
                url(LOGOUT_ENDPOINT), HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);

        // Jakarta's Cookie.setMaxAge(0) renders as a past Expires date rather than "Max-Age=0";
        // browsers treat both as an immediate deletion. Anchor on the value being empty (the
        // "cleared" wire signal) rather than the header syntax.
        List<String> setCookies = response.getHeaders().get("Set-Cookie");
        assertThat(setCookies).anyMatch(c -> c.startsWith("argus_session=;"));
        assertThat(setCookies).anyMatch(c -> c.startsWith("argus_csrf=;"));

        Integer sessionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sessions WHERE user_id = ?", Integer.class, userId);
        assertThat(sessionCount).isZero();
    }

    @Test
    void postLogout_noSession_returns401Unauthorized() {
        ResponseEntity<String> response = http.exchange(
                url(LOGOUT_ENDPOINT), HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void postLogout_missingCsrfHeader_returns403Forbidden() {
        String email = "logout-nocsrf@example.com";
        createVerifiedUser(email);
        Session session = login(email);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE,
                "argus_session=" + session.sessionCookie + "; argus_csrf=" + session.csrfCookie);
        // Deliberately omit X-CSRF-Token.
        ResponseEntity<String> response = http.exchange(
                url(LOGOUT_ENDPOINT), HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    // --- GET /auth/status ----------------------------------------------------------------------

    @Test
    void getStatus_validSession_returns200WithUserIdAndExpiresAt() {
        String email = "status-happy@example.com";
        UUID userId = createVerifiedUser(email);
        Session session = login(email);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "argus_session=" + session.sessionCookie);
        ResponseEntity<String> response = http.exchange(
                url(STATUS_ENDPOINT), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json.readTree(response.getBody()).get("data");
        assertThat(data.get("user_id").asString()).isEqualTo(userId.toString());
        assertThat(data.get("expires_at").asString()).isNotEmpty();
    }

    @Test
    void getStatus_noSession_returns401Unauthorized() {
        ResponseEntity<String> response = http.exchange(
                url(STATUS_ENDPOINT), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // --- POST /auth/verify-email ---------------------------------------------------------------

    @Test
    void postVerifyEmail_validToken_returns204AndMarksUserAndVerificationVerified() {
        String email = "verify-happy@example.com";
        SignedUpUser signup = signupAndReadOutboxToken(email);

        ResponseEntity<String> response = postVerifyEmail(verifyBody(signup.plainToken));

        assertThat(response.getStatusCode().value()).isEqualTo(204);

        Boolean isVerified = jdbc.queryForObject(
                "SELECT is_verified FROM users WHERE id = ?", Boolean.class, signup.userId);
        assertThat(isVerified).isTrue();

        Object verifiedAt = jdbc.queryForMap(
                "SELECT verified_at FROM email_verifications WHERE user_id = ?", signup.userId)
                .get("verified_at");
        assertThat(verifiedAt).isNotNull();
    }

    @Test
    void postVerifyEmail_expiredToken_returns422InvalidTokenAndLeavesUserUnverified() {
        String email = "verify-expired@example.com";
        SignedUpUser signup = signupAndReadOutboxToken(email);
        // Domain invariant requires expires_at > created_at; back-date both so the row still
        // validates when the JPA repository maps it back to the record.
        jdbc.update("UPDATE email_verifications "
                        + "SET created_at = now() - interval '2 hours', "
                        + "    expires_at = now() - interval '1 hour' "
                        + "WHERE user_id = ?",
                signup.userId);

        ResponseEntity<String> response = postVerifyEmail(verifyBody(signup.plainToken));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("INVALID_TOKEN");
        Boolean isVerified = jdbc.queryForObject(
                "SELECT is_verified FROM users WHERE id = ?", Boolean.class, signup.userId);
        assertThat(isVerified).isFalse();
    }

    @Test
    void postVerifyEmail_alreadyUsedToken_returns422InvalidToken() {
        String email = "verify-replayed@example.com";
        SignedUpUser signup = signupAndReadOutboxToken(email);
        assertThat(postVerifyEmail(verifyBody(signup.plainToken)).getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> response = postVerifyEmail(verifyBody(signup.plainToken));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void postVerifyEmail_unknownToken_returns422InvalidToken() {
        ResponseEntity<String> response = postVerifyEmail(verifyBody("no-such-token"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void postVerifyEmail_missingToken_returns422ValidationError() {
        ResponseEntity<String> response = postVerifyEmail("{}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("VALIDATION_ERROR");
    }

    // --- POST /auth/password-reset-requests ----------------------------------------------------

    @Test
    void postPasswordResetRequests_registeredEmail_returns202AndSeedsResetRowAndOutbox() {
        String email = "reset-happy@example.com";
        UUID userId = createVerifiedUser(email);

        ResponseEntity<String> response = postPasswordResetRequest(passwordResetRequestBody(email));

        assertThat(response.getStatusCode().value()).isEqualTo(202);

        Map<String, Object> resetRow = jdbc.queryForMap(
                "SELECT * FROM password_resets WHERE user_id = ?", userId);
        assertThat(resetRow.get("token_hash")).asString().matches("[0-9a-f]{64}");
        assertThat(resetRow.get("claimed_at")).isNull();

        Map<String, Object> outboxRow = jdbc.queryForMap(
                "SELECT event_type, idempotence_key, payload::text AS payload "
                        + "FROM outbox WHERE aggregate_id = ?",
                userId);
        assertThat(outboxRow.get("event_type")).isEqualTo("email.password_reset");
        assertThat(outboxRow.get("idempotence_key")).asString().startsWith("email.password_reset:");
        JsonNode payload = json.readTree((String) outboxRow.get("payload"));
        assertThat(payload.get("user_id").asString()).isEqualTo(userId.toString());
        assertThat(payload.get("email").asString()).isEqualTo(email);
        assertThat(payload.get("token").asString()).matches("[A-Za-z0-9_-]+");
        // The token in the outbox is the plain form (goes into the email link); DB stores only the hash.
        assertThat(payload.get("token").asString()).isNotEqualTo(resetRow.get("token_hash"));
    }

    @Test
    void postPasswordResetRequests_unknownEmail_returns202AndDoesNotSeedResetOrOutbox() {
        ResponseEntity<String> response = postPasswordResetRequest(
                passwordResetRequestBody("no-such-user@example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);

        Integer resetCount = jdbc.queryForObject("SELECT COUNT(*) FROM password_resets", Integer.class);
        assertThat(resetCount).isZero();
        Integer outboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'email.password_reset'", Integer.class);
        assertThat(outboxCount).isZero();
    }

    @Test
    void postPasswordResetRequests_suspendedUser_returns202AndDoesNotSeedResetOrOutbox() {
        String email = "reset-suspended@example.com";
        UUID userId = createVerifiedUser(email);
        jdbc.update("UPDATE users SET is_suspended = TRUE WHERE id = ?", userId);

        ResponseEntity<String> response = postPasswordResetRequest(passwordResetRequestBody(email));

        assertThat(response.getStatusCode().value()).isEqualTo(202);

        Integer resetCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_resets WHERE user_id = ?", Integer.class, userId);
        assertThat(resetCount).isZero();
        Integer outboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ?", Integer.class, userId);
        assertThat(outboxCount).isZero();
    }

    @Test
    void postPasswordResetRequests_malformedEmail_returns422ValidationError() {
        ResponseEntity<String> response = postPasswordResetRequest(passwordResetRequestBody("not-an-email"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("VALIDATION_ERROR");
    }

    // --- POST /auth/password-resets ------------------------------------------------------------

    @Test
    void postPasswordResets_validToken_returns204ClaimsTokenAndRemovesSessionsAndSwitchesPassword() {
        String email = "complete-happy@example.com";
        final UUID userId = createVerifiedUser(email);
        // Live session that must be gone after the reset commits.
        login(email);
        String plainToken = seedResetTokenViaEndpoint(email);

        ResponseEntity<String> response = postPasswordResetComplete(
                passwordResetCompleteBody(plainToken, NEW_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().get("Set-Cookie")).isNull();

        Object claimedAt = jdbc.queryForMap(
                "SELECT claimed_at FROM password_resets WHERE user_id = ?", userId).get("claimed_at");
        assertThat(claimedAt).isNotNull();

        Integer sessionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sessions WHERE user_id = ?", Integer.class, userId);
        assertThat(sessionCount).isZero();

        // The old password no longer authenticates; the new one does.
        assertThat(postLogin(loginBody(email, VALID_PASSWORD)).getStatusCode().value()).isEqualTo(401);
        assertThat(postLogin(loginBody(email, NEW_PASSWORD)).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void postPasswordResets_replayedToken_returns422InvalidToken() {
        String email = "complete-replay@example.com";
        createVerifiedUser(email);
        String plainToken = seedResetTokenViaEndpoint(email);
        assertThat(postPasswordResetComplete(passwordResetCompleteBody(plainToken, NEW_PASSWORD))
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> response = postPasswordResetComplete(
                passwordResetCompleteBody(plainToken, "another-password"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void postPasswordResets_expiredToken_returns422InvalidToken() {
        String email = "complete-expired@example.com";
        UUID userId = createVerifiedUser(email);
        String plainToken = seedResetTokenViaEndpoint(email);
        // Domain invariant requires expires_at > created_at; back-date both so the record stays valid.
        jdbc.update("UPDATE password_resets "
                        + "SET created_at = now() - interval '2 hours', "
                        + "    expires_at = now() - interval '1 hour' "
                        + "WHERE user_id = ?",
                userId);

        ResponseEntity<String> response = postPasswordResetComplete(
                passwordResetCompleteBody(plainToken, NEW_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void postPasswordResets_unknownToken_returns422InvalidToken() {
        ResponseEntity<String> response = postPasswordResetComplete(
                passwordResetCompleteBody("no-such-token", NEW_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void postPasswordResets_userSuspendedBetweenRequestAndComplete_returns403AccountSuspendedAndLeavesTokenUnclaimed() {
        String email = "complete-suspended@example.com";
        UUID userId = createVerifiedUser(email);
        String plainToken = seedResetTokenViaEndpoint(email);
        // The user got suspended after receiving the reset link but before clicking it.
        jdbc.update("UPDATE users SET is_suspended = TRUE WHERE id = ?", userId);

        ResponseEntity<String> response = postPasswordResetComplete(
                passwordResetCompleteBody(plainToken, NEW_PASSWORD));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("ACCOUNT_SUSPENDED");
        Object claimedAt = jdbc.queryForMap(
                "SELECT claimed_at FROM password_resets WHERE user_id = ?", userId).get("claimed_at");
        assertThat(claimedAt).isNull();
    }

    @Test
    void postPasswordResets_shortNewPassword_returns422ValidationError() {
        ResponseEntity<String> response = postPasswordResetComplete(
                passwordResetCompleteBody("some-token", "short"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void postPasswordResets_missingToken_returns422ValidationError() {
        ResponseEntity<String> response = postPasswordResetComplete(
                "{\"new_password\":\"" + NEW_PASSWORD + "\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(json.readTree(response.getBody()).get("error").get("code").asString())
                .isEqualTo("VALIDATION_ERROR");
    }

    // --- Helpers -------------------------------------------------------------------------------

    private UUID createVerifiedUser(String email) {
        User created = userService.createUnverified(email, VALID_PASSWORD);
        userService.markVerified(created.id());
        return created.id().value();
    }

    private Session login(String email) {
        ResponseEntity<String> response = postLogin(loginBody(email, VALID_PASSWORD));
        Map<String, String> cookies = setCookiesByName(response);
        return new Session(cookies.get("argus_session"), cookies.get("argus_csrf"));
    }

    private static Map<String, String> setCookiesByName(ResponseEntity<String> response) {
        List<String> setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) {
            return Map.of();
        }
        // If a name appears more than once (e.g. logout produces refreshed + cleared), the last
        // Set-Cookie wins, which mirrors the browser's application order.
        return setCookies.stream().collect(Collectors.toMap(
                AuthControllerIT::cookieName,
                AuthControllerIT::cookieValue,
                (first, second) -> second));
    }

    private static String cookieName(String setCookie) {
        int eq = setCookie.indexOf('=');
        return setCookie.substring(0, eq);
    }

    private static String cookieValue(String setCookie) {
        int eq = setCookie.indexOf('=');
        int semi = setCookie.indexOf(';');
        return semi < 0 ? setCookie.substring(eq + 1) : setCookie.substring(eq + 1, semi);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> postLogin(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(LOGIN_ENDPOINT), HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String body(String email, String password) {
        // Hand-built JSON avoids leaking test-side serialization choices into the wire format.
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private ResponseEntity<String> post(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers),
                String.class);
    }

    private static java.util.List<String> fieldNames(JsonNode details) {
        java.util.List<String> names = new java.util.ArrayList<>();
        details.forEach(node -> names.add(node.get("field").asString()));
        return names;
    }

    private record Session(String sessionCookie, String csrfCookie) {}

    private record SignedUpUser(UUID userId, String plainToken) {}

    private SignedUpUser signupAndReadOutboxToken(String email) {
        ResponseEntity<String> signupResponse = post(body(email, VALID_PASSWORD));
        UUID userId = UUID.fromString(
                json.readTree(signupResponse.getBody()).get("data").get("user_id").asString());
        // The plain token only ever exists in the outbox payload (destined for the verification
        // email); the DB stores just the SHA-256 hash. Reading it here mirrors what the email
        // gateway will hand to the recipient.
        String payload = (String) jdbc.queryForMap(
                "SELECT payload::text AS payload FROM outbox WHERE aggregate_id = ?", userId)
                .get("payload");
        String plainToken = json.readTree(payload).get("token").asString();
        return new SignedUpUser(userId, plainToken);
    }

    private ResponseEntity<String> postVerifyEmail(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(VERIFY_EMAIL_ENDPOINT), HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    private static String verifyBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private ResponseEntity<String> postPasswordResetRequest(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(PASSWORD_RESET_REQUEST_ENDPOINT), HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    private ResponseEntity<String> postPasswordResetComplete(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(PASSWORD_RESET_COMPLETE_ENDPOINT), HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    private static String passwordResetRequestBody(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private static String passwordResetCompleteBody(String token, String newPassword) {
        return "{\"token\":\"" + token + "\",\"new_password\":\"" + newPassword + "\"}";
    }

    // Drives the request endpoint end-to-end so the persisted row and outbox payload mirror what a
    // real caller would produce; then reads the plain token from the outbox payload — the only
    // place it exists outside the outbound email.
    private String seedResetTokenViaEndpoint(String email) {
        assertThat(postPasswordResetRequest(passwordResetRequestBody(email)).getStatusCode().value())
                .isEqualTo(202);
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        String payload = (String) jdbc.queryForMap(
                "SELECT payload::text AS payload FROM outbox WHERE aggregate_id = ?", userId)
                .get("payload");
        return json.readTree(payload).get("token").asString();
    }
}
