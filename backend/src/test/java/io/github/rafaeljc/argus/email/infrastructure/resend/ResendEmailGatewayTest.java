package io.github.rafaeljc.argus.email.infrastructure.resend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.common.domain.ServiceUnavailableException;
import io.github.rafaeljc.argus.email.application.SendResult;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import io.github.rafaeljc.argus.email.infrastructure.template.EmailTemplateRenderer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ResendEmailGatewayTest {

    private static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");
    private static final String BASE_URL = "https://api.resend.example";
    private static final String SEND_URL = BASE_URL + "/emails";
    private static final String API_KEY = "re_test_key";
    private static final String FROM = "argus@argus.example";
    private static final String IDEMPOTENCE_KEY = "email.verification:0d5f";

    private static final String PAYLOAD =
            """
            {"user_id":"7c1e","email":"alice@example.com","token":"tok-abc",\
            "expires_at":"2026-03-12T12:00:00Z"}
            """;

    private MockRestServiceServer server;
    private ResendEmailGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ResendProperties properties =
                new ResendProperties(API_KEY, BASE_URL, Duration.ofSeconds(5), Duration.ofSeconds(30));
        Retry retry = Retry.of(
                "test",
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(
                                HttpServerErrorException.class, ResourceAccessException.class, IOException.class)
                        .build());
        gateway = new ResendEmailGateway(
                builder,
                properties,
                FROM,
                new EmailTemplateRenderer(new ObjectMapper(), "https://app.argus.example"),
                retry);
    }

    private static OutboxMessage message(String payload) {
        return new OutboxMessage(
                new OutboxId(UUID.randomUUID()),
                UUID.randomUUID(),
                EventType.VERIFICATION,
                payload,
                IDEMPOTENCE_KEY,
                NOW,
                null,
                0,
                null,
                null);
    }

    private static org.springframework.test.web.client.ResponseActions expectSend(MockRestServiceServer server) {
        return server.expect(requestTo(SEND_URL)).andExpect(method(HttpMethod.POST));
    }

    @Test
    void send_vendorAccepts_returnsSuccess() {
        expectSend(server).andRespond(withSuccess("{\"id\":\"msg-1\"}", MediaType.APPLICATION_JSON));

        SendResult result = gateway.send(message(PAYLOAD));

        assertThat(result).isEqualTo(new SendResult(true, null));
        server.verify();
    }

    @Test
    void send_apiKey_isSentAsBearerHeader() {
        expectSend(server)
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess("{\"id\":\"msg-1\"}", MediaType.APPLICATION_JSON));

        gateway.send(message(PAYLOAD));

        server.verify();
    }

    // The outbox guarantees at-least-once delivery, so a retried poll can present the same message
    // twice. Handing the vendor our idempotence key makes the duplicate its problem to swallow.
    @Test
    void send_idempotenceKey_isForwardedAsTheVendorDedupHeader() {
        expectSend(server)
                .andExpect(header("Idempotency-Key", IDEMPOTENCE_KEY))
                .andRespond(withSuccess("{\"id\":\"msg-1\"}", MediaType.APPLICATION_JSON));

        gateway.send(message(PAYLOAD));

        server.verify();
    }

    @Test
    void send_requestBody_carriesRenderedRecipientSubjectAndBothBodies() {
        expectSend(server)
                .andExpect(jsonPath("$.from").value(FROM))
                .andExpect(jsonPath("$.to[0]").value("alice@example.com"))
                .andExpect(jsonPath("$.subject").exists())
                .andExpect(jsonPath("$.html").exists())
                .andExpect(jsonPath("$.text").exists())
                .andRespond(withSuccess("{\"id\":\"msg-1\"}", MediaType.APPLICATION_JSON));

        gateway.send(message(PAYLOAD));

        server.verify();
    }

    // A rejected recipient never becomes valid by trying again. It must not reach the circuit
    // breaker either: one user's dead address is not a vendor outage.
    @Test
    void send_vendorRejectsRecipient_returnsFailureWithoutRetryingOrThrowing() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).body("{\"message\":\"invalid to\"}"));

        SendResult result = gateway.send(message(PAYLOAD));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("422");
        server.verify();
    }

    // Throttling is not a vendor failure: the next 30s poll tick is the retry, and tripping the
    // breaker here would stall the whole queue over a rate limit we are already respecting.
    @Test
    void send_rateLimited_returnsFailureWithoutThrowing() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        SendResult result = gateway.send(message(PAYLOAD));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("429");
        server.verify();
    }

    // A rejected key fails every message equally, so it must not be charged to the message. Left as
    // a returned failure it would burn one error_count per message per poll, and the queue would be
    // exhausted and unclaimable within minutes — with the breaker closed and readiness green.
    @Test
    void send_credentialsRejected_throwsServiceUnavailableWithoutRetrying() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.send(message(PAYLOAD))).isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }

    @Test
    void send_senderNotAuthorised_throwsServiceUnavailableWithoutRetrying() {
        server.expect(ExpectedCount.once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> gateway.send(message(PAYLOAD))).isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }

    // Must throw rather than return failure: only a thrown exception reaches the circuit breaker
    // that PollOutboxOnce wraps around this call.
    @Test
    void send_serverError_retriesThreeTimesThenThrowsServiceUnavailable() {
        server.expect(ExpectedCount.times(3), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> gateway.send(message(PAYLOAD))).isInstanceOf(ServiceUnavailableException.class);

        server.verify();
    }

    @Test
    void send_connectionFailure_isRetriedThenSucceeds() {
        server.expect(ExpectedCount.times(2), requestTo(SEND_URL))
                .andRespond(withException(new IOException("connection reset")));
        server.expect(requestTo(SEND_URL)).andRespond(withSuccess("{\"id\":\"msg-1\"}", MediaType.APPLICATION_JSON));

        SendResult result = gateway.send(message(PAYLOAD));

        assertThat(result.success()).isTrue();
        server.verify();
    }

    // An unrenderable payload is our bug, not the vendor's. It must not count against the breaker,
    // and there is nothing to send.
    @Test
    void send_payloadCannotBeRendered_returnsFailureWithoutCallingTheVendor() {
        SendResult result = gateway.send(message("not json"));

        assertThat(result.success()).isFalse();
        server.verify();
    }
}
