package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.rafaeljc.argus.common.application.audit.AuthAuditListener;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "argus.rate-limit.buckets.[RL.auth.signup].capacity=1000",
        "argus.rate-limit.buckets.[RL.auth.signup].refill-tokens=1000",
        "argus.rate-limit.buckets.[RL.auth.signup].refill-duration=PT1M",
        "argus.rate-limit.buckets.[RL.unauth.global].capacity=1000",
        "argus.rate-limit.buckets.[RL.unauth.global].refill-tokens=1000",
        "argus.rate-limit.buckets.[RL.unauth.global].refill-duration=PT1M"
})
class AuthAuditEmissionIT {

    private static final String SIGNUP_ENDPOINT = "/api/v1/auth/signup";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    private ListAppender<ILoggingEvent> appender;
    private Logger sink;

    @BeforeEach
    void attachAppender() {
        sink = (Logger) LoggerFactory.getLogger(AuthAuditListener.class);
        appender = new ListAppender<>();
        appender.start();
        sink.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        sink.detachAppender(appender);
    }

    @Test
    void postSignup_validRequest_emitsAuthSignupSuccessEventWithUserIdAndEmail() {
        String email = "audit-signup-" + System.nanoTime() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct horse battery staple\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.postForEntity(
                "http://localhost:" + port + SIGNUP_ENDPOINT,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);

        ILoggingEvent event = appender.list.stream()
                .filter(e -> "auth.signup".equals(e.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no auth.signup log line among: " + appender.list));

        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        List<String> args = Arrays.stream(event.getArgumentArray())
                .map(Object::toString)
                .toList();
        assertThat(args).contains("event=auth.signup", "status=success", "email=" + email);
        assertThat(args).anyMatch(a -> a.startsWith("user_id="));

        String rendered = event.getFormattedMessage() + " " + args;
        assertThat(rendered).doesNotContain("password").doesNotContain("token");
    }
}
