package io.github.rafaeljc.argus.email.infrastructure.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EmailTemplateRendererTest {

    private static final Instant NOW = Instant.parse("2026-03-11T12:00:00Z");
    private static final String APP_BASE_URL = "https://app.argus.example";

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer(new ObjectMapper(), APP_BASE_URL);

    private static OutboxMessage message(EventType eventType, String payload) {
        return new OutboxMessage(
                new OutboxId(UUID.randomUUID()),
                UUID.randomUUID(),
                eventType,
                payload,
                "key-1",
                NOW,
                null,
                0,
                null,
                null);
    }

    private static String verificationPayload(String email, String token) {
        String template =
                """
                {"user_id":"7c1e...","email":"%s","token":"%s","expires_at":"2026-03-12T12:00:00Z"}
                """;
        return template.formatted(email, token);
    }

    @Test
    void render_verification_addressesTheRecipientFromThePayload() {
        RenderedEmail email = renderer.render(message(
                EventType.VERIFICATION, verificationPayload("alice@example.com", "tok-abc")));

        assertThat(email.to()).isEqualTo("alice@example.com");
    }

    @Test
    void render_verification_linksToTheVerifyEmailPageWithTheToken() {
        RenderedEmail email = renderer.render(message(
                EventType.VERIFICATION, verificationPayload("alice@example.com", "tok-abc")));

        assertThat(email.html()).contains(APP_BASE_URL + "/verify-email?token=tok-abc");
        assertThat(email.text()).contains(APP_BASE_URL + "/verify-email?token=tok-abc");
    }

    @Test
    void render_verification_subjectIsNotBlank() {
        RenderedEmail email = renderer.render(message(
                EventType.VERIFICATION, verificationPayload("alice@example.com", "tok-abc")));

        assertThat(email.subject()).isNotBlank();
    }

    // Tokens are opaque vendor-facing strings; a raw '+' or '&' would silently truncate the query
    // parameter and produce a link that reports "invalid token" to a user who did nothing wrong.
    @Test
    void render_verification_tokenWithUrlSpecialCharacters_isPercentEncoded() {
        RenderedEmail email = renderer.render(message(
                EventType.VERIFICATION, verificationPayload("alice@example.com", "a+b&c=d")));

        assertThat(email.html()).contains("/verify-email?token=a%2Bb%26c%3Dd");
    }

    @Test
    void render_passwordReset_linksToThePasswordResetConfirmPage() {
        RenderedEmail email = renderer.render(message(
                EventType.PASSWORD_RESET, verificationPayload("bob@example.com", "tok-reset")));

        assertThat(email.to()).isEqualTo("bob@example.com");
        assertThat(email.html()).contains(APP_BASE_URL + "/password-reset/confirm?token=tok-reset");
    }

    @Test
    void render_digest_listsEveryFiringAndLinksToTheFiringsPage() {
        String payload =
                """
                {"user_id":"7c1e...","email":"carol@example.com","run_date":"2026-03-11",
                 "firings":[
                   {"direction":"DOWN","threshold":"5.00","window_days":"7",
                    "percent_change":"-6.20","window_start_date":"2026-03-04",
                    "window_end_date":"2026-03-11","portfolio_value_start":"10000.00",
                    "portfolio_value_end":"9380.00","rule_id":"r-1"},
                   {"direction":"UP","threshold":"3.00","window_days":"30",
                    "percent_change":"4.10","window_start_date":"2026-02-09",
                    "window_end_date":"2026-03-11","portfolio_value_start":"9000.00",
                    "portfolio_value_end":"9369.00","rule_id":"r-2"}]}
                """;

        RenderedEmail email = renderer.render(message(EventType.DIGEST, payload));

        assertThat(email.to()).isEqualTo("carol@example.com");
        assertThat(email.text()).contains("-6.20").contains("4.10");
        assertThat(email.html()).contains(APP_BASE_URL + "/alerts/firings");
    }

    @Test
    void render_digest_noFirings_stillRendersWithoutFiringLines() {
        String payload =
                """
                {"user_id":"7c1e...","email":"carol@example.com","run_date":"2026-03-11","firings":[]}
                """;

        RenderedEmail email = renderer.render(message(EventType.DIGEST, payload));

        assertThat(email.to()).isEqualTo("carol@example.com");
        assertThat(email.html()).contains(APP_BASE_URL + "/alerts/firings");
    }

    // The payload is our own JSON, not user input, but the email address inside it is user-supplied
    // and lands in an HTML document.
    @Test
    void render_payloadFieldContainingMarkup_isEscapedInTheHtmlBody() {
        RenderedEmail email = renderer.render(message(
                EventType.VERIFICATION, verificationPayload("<script>x</script>@example.com", "tok-abc")));

        assertThat(email.html()).doesNotContain("<script>");
    }

    @Test
    void render_payloadIsNotJson_throwsIllegalArgument() {
        assertThatThrownBy(() -> renderer.render(message(EventType.VERIFICATION, "not json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void render_payloadMissingRequiredField_throwsIllegalArgument() {
        assertThatThrownBy(() -> renderer.render(message(
                        EventType.VERIFICATION, "{\"email\":\"alice@example.com\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void render_payloadMissingRecipient_throwsIllegalArgument() {
        assertThatThrownBy(() -> renderer.render(message(EventType.VERIFICATION, "{\"token\":\"tok-abc\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }
}
