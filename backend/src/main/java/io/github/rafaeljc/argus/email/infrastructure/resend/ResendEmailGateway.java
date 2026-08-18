package io.github.rafaeljc.argus.email.infrastructure.resend;

import io.github.rafaeljc.argus.common.domain.ServiceUnavailableException;
import io.github.rafaeljc.argus.email.application.SendResult;
import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import io.github.rafaeljc.argus.email.infrastructure.template.EmailTemplateRenderer;
import io.github.rafaeljc.argus.email.infrastructure.template.RenderedEmail;
import io.github.resilience4j.retry.Retry;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Email adapter over the vendor's REST API.
//
// Retry sits *inside* this class and the circuit breaker sits outside it, in PollOutboxOnce. That
// ordering matters: transient errors are exhausted here, so the breaker's sliding window records
// one outcome per logical send. The other way round, every retry attempt would count as its own
// failure and trip the breaker after a third of the sends it should take.
//
// The return-versus-throw split is the whole contract with the poller. A returned failure is
// terminal for this message and invisible to the breaker; a thrown exception is what the breaker
// counts. So anything the vendor blamed on *this message* is returned, and anything that says the
// vendor itself is unwell is thrown.
public class ResendEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailGateway.class);

    private static final String SEND_PATH = "/emails";
    // The vendor's error bodies are short, but last_error is TEXT and this ends up in every log
    // line for the message. Cap it so a pathological response can't dominate either.
    private static final int MAX_ERROR_LENGTH = 500;

    private final RestClient client;
    private final String fromAddress;
    private final EmailTemplateRenderer renderer;
    private final Retry retry;

    public ResendEmailGateway(
            RestClient.Builder builder,
            ResendProperties properties,
            String fromAddress,
            EmailTemplateRenderer renderer,
            Retry vendorEmailRetry) {
        // Transport settings (timeouts) are applied to the builder by the caller, so tests can
        // substitute one.
        this.client = builder.baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
        this.fromAddress = fromAddress;
        this.renderer = renderer;
        this.retry = vendorEmailRetry;
    }

    @Override
    public SendResult send(OutboxMessage message) {
        RenderedEmail email;
        try {
            email = renderer.render(message);
        } catch (RuntimeException ex) {
            // A payload we wrote and cannot read back is our bug. Retrying never fixes it, and
            // letting it out would charge the breaker for a fault the vendor had no part in.
            log.error("outbox message {} could not be rendered", message.id().value(), ex);
            return new SendResult(false, truncate("render failed: " + ex.getMessage()));
        }
        return dispatch(message, email);
    }

    private SendResult dispatch(OutboxMessage message, RenderedEmail email) {
        Supplier<ResendSendResponse> request = () -> client.post()
                .uri(SEND_PATH)
                // The outbox guarantees at-least-once delivery, so the same message can be
                // presented twice after a crash between send and mark-published. The vendor
                // deduplicates on this key, which turns that into a no-op instead of a second copy
                // in the user's inbox.
                .header("Idempotency-Key", message.idempotenceKey())
                .body(new ResendSendRequest(
                        fromAddress, List.of(email.to()), email.subject(), email.html(), email.text()))
                .retrieve()
                .body(ResendSendResponse.class);
        try {
            ResendSendResponse response = Retry.decorateSupplier(retry, request).get();
            log.info(
                    "vendor email accepted: vendorMessageId={} eventType={} idempotenceKey={}",
                    response == null ? null : response.id(),
                    message.eventType(),
                    message.idempotenceKey());
            return new SendResult(true, null);
        } catch (HttpClientErrorException ex) {
            // 401/403 are the exception to the rule below: they are scoped to the account, not to
            // this message, so every message fails identically. Returned as a terminal failure they
            // would spend one error_count per message per poll and render the whole queue
            // unclaimable within minutes, with the breaker closed and readiness green throughout.
            // Thrown, the breaker opens and the queue is preserved until the key is fixed.
            if (isCredentialFailure(ex)) {
                log.error("vendor email credentials rejected: {}", ex.getStatusCode());
                throw new ServiceUnavailableException("vendor email credentials rejected", ex);
            }
            // Everything else in 4xx is about this request, not the vendor's health: a suppressed
            // or malformed recipient. 429 lands here too — throttling is not failure, and the next
            // poll tick is already the retry, so opening the breaker over it would stall the whole
            // queue for a limit we are respecting.
            log.warn("vendor email rejected message {}: {}", message.id().value(), ex.getStatusCode());
            return new SendResult(false, truncate(ex.getStatusCode() + " " + ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            log.warn("vendor email send failed for message {}", message.id().value(), ex);
            throw new ServiceUnavailableException("vendor email send failed", ex);
        }
    }

    private static boolean isCredentialFailure(HttpClientErrorException ex) {
        HttpStatusCode status = ex.getStatusCode();
        return status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.FORBIDDEN);
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "unknown vendor error";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
