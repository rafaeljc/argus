package io.github.rafaeljc.argus.email.infrastructure.template;

import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Turns an outbox row into the subject and bodies the vendor sends. Lives in infrastructure, not
// application, because it parses the payload JSON and Jackson is barred from the inner layers.
//
// Plain text blocks rather than a template engine: three templates do not pay for another
// dependency, and the compiler checks the placeholders. Both bodies are always produced — HTML for
// the ordinary case, text for clients that refuse it and for spam scoring.
public class EmailTemplateRenderer {

    private static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final String appBaseUrl;

    public EmailTemplateRenderer(ObjectMapper objectMapper, String appBaseUrl) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            throw new IllegalArgumentException("appBaseUrl must not be blank");
        }
        this.objectMapper = objectMapper;
        // Links are built as base + "/path", so a configured trailing slash would yield "//verify-email".
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    public RenderedEmail render(OutboxMessage message) {
        JsonNode payload = parse(message.payload());
        String recipient = required(payload, "email");
        return switch (message.eventType()) {
            case VERIFICATION -> verification(recipient, payload);
            case PASSWORD_RESET -> passwordReset(recipient, payload);
            case DIGEST -> digest(recipient, payload);
        };
    }

    private RenderedEmail verification(String recipient, JsonNode payload) {
        String url = link("/verify-email", required(payload, "token"));
        String expiry = expiry(payload);
        String text =
                """
                Welcome to Argus.

                Argus tracks your portfolio at each day's close and tells you when it moves \
                more than you asked it to. Confirm this address to finish setting up your account:

                %s

                The link works once and expires on %s. If it has already expired, sign in and \
                request a new one.

                If you did not sign up for Argus, no account was created and you can ignore this message.

                -- Argus, end-of-day portfolio monitoring
                """
                        .formatted(url, expiry);
        String body =
                """
                <p>Argus tracks your portfolio at each day's close and tells you when it moves \
                more than you asked it to. Confirm this address to finish setting up your account.</p>
                <p><a href="%s">Confirm my email address</a></p>
                <p>The link works once and expires on %s. If it has already expired, sign in and \
                request a new one.</p>
                <p>If you did not sign up for Argus, no account was created and you can ignore this message.</p>
                """
                        .formatted(url, HtmlUtils.htmlEscape(expiry));
        return new RenderedEmail(recipient, "Confirm your email to finish setting up Argus", page(body, url), text);
    }

    private RenderedEmail passwordReset(String recipient, JsonNode payload) {
        String url = link("/password-reset/confirm", required(payload, "token"));
        String expiry = expiry(payload);
        String text =
                """
                Someone asked to reset the password for the Argus account registered to %s.

                Choose a new password here:

                %s

                The link works once and expires on %s.

                If this was not you, ignore this message. Your current password stays active and \
                nobody can use the link without access to this mailbox.

                -- Argus, end-of-day portfolio monitoring
                """
                        .formatted(recipient, url, expiry);
        String body =
                """
                <p>Someone asked to reset the password for the Argus account registered to <strong>%s</strong>.</p>
                <p><a href="%s">Choose a new password</a></p>
                <p>The link works once and expires on %s.</p>
                <p>If this was not you, ignore this message. Your current password stays active and \
                nobody can use the link without access to this mailbox.</p>
                """
                        .formatted(HtmlUtils.htmlEscape(recipient), url, HtmlUtils.htmlEscape(expiry));
        return new RenderedEmail(recipient, "Reset your Argus password", page(body, url), text);
    }

    private RenderedEmail digest(String recipient, JsonNode payload) {
        String runDate = required(payload, "run_date");
        List<Firing> firings = firings(payload);
        String url = appBaseUrl + "/alerts/firings";
        String headline = firings.size() == 1
                ? "One of your alert rules fired at the close on %s.".formatted(runDate)
                : "%d of your alert rules fired at the close on %s.".formatted(firings.size(), runDate);
        String text =
                """
                %s

                %s

                Review the full firing history, and the rules behind it, here:

                %s

                Each rule fires once. Re-arm the ones you still want to watch from the same page.

                -- Argus, end-of-day portfolio monitoring
                """
                        .formatted(headline, textLines(firings), url);
        String body =
                """
                <p>%s</p>
                <ul>%s</ul>
                <p><a href="%s">Review the full firing history</a></p>
                <p>Each rule fires once. Re-arm the ones you still want to watch from the same page.</p>
                """
                        .formatted(HtmlUtils.htmlEscape(headline), htmlLines(firings), url);
        return new RenderedEmail(recipient, "Argus alerts for " + runDate, page(body, url), text);
    }

    // Deliberately thin chrome: a wrapper div, a wordmark, and a footer. Mail clients drop <style>
    // blocks, so the little styling there is has to be inline.
    private static String page(String body, String url) {
        String template =
                """
                <!doctype html>
                <html lang="en"><body style="margin:0;padding:24px;background:#f2f4f7;">
                <div style="max-width:560px;margin:0 auto;padding:28px 32px;background:#ffffff;\
                border-radius:12px;font-family:Helvetica,Arial,sans-serif;font-size:15px;\
                line-height:24px;color:#101828;">
                <p style="margin:0 0 20px 0;font-size:13px;font-weight:700;letter-spacing:0.1em;\
                color:#1a56db;">ARGUS</p>
                %s
                <p style="margin:24px 0 0 0;font-size:12px;line-height:20px;color:#667085;">
                If the link above does not work, paste this into your browser:<br>
                <span style="word-break:break-all;">%s</span></p>
                <p style="margin:16px 0 0 0;font-size:12px;line-height:20px;color:#667085;">
                Argus &middot; end-of-day portfolio monitoring</p>
                </div></body></html>
                """;
        return template.formatted(body, url);
    }

    private static String textLines(List<Firing> firings) {
        return firings.stream().map(Firing::asText).reduce((a, b) -> a + "\n\n" + b).orElse("");
    }

    private static String htmlLines(List<Firing> firings) {
        return firings.stream().map(Firing::asHtml).reduce("", String::concat);
    }

    private static List<Firing> firings(JsonNode payload) {
        List<Firing> firings = new ArrayList<>();
        for (JsonNode node : payload.path("firings")) {
            firings.add(new Firing(
                    required(node, "direction"),
                    required(node, "percent_change"),
                    required(node, "window_days"),
                    required(node, "window_start_date"),
                    required(node, "window_end_date"),
                    required(node, "portfolio_value_start"),
                    required(node, "portfolio_value_end")));
        }
        return List.copyOf(firings);
    }

    // The sign on percent_change already carries the direction, so the wording does not repeat it;
    // `direction` only decides the colour in the HTML body.
    private record Firing(
            String direction,
            String percentChange,
            String windowDays,
            String startDate,
            String endDate,
            String valueStart,
            String valueEnd) {

        private String headline() {
            return "%s%% over %s days".formatted(percentChange, windowDays);
        }

        private String detail() {
            return "%s to %s, portfolio value %s to %s".formatted(startDate, endDate, valueStart, valueEnd);
        }

        String asText() {
            return "  " + headline() + "\n    " + detail();
        }

        String asHtml() {
            String colour = "DOWN".equals(direction) ? "#b42318" : "#067647";
            String template =
                    """
                    <li style="margin-bottom:10px;"><strong style="color:%s;">%s</strong><br>
                    <span style="font-size:13px;color:#667085;">%s</span></li>
                    """;
            return template.formatted(colour, HtmlUtils.htmlEscape(headline()), HtmlUtils.htmlEscape(detail()));
        }
    }

    private String link(String path, String token) {
        return appBaseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String expiry(JsonNode payload) {
        String raw = required(payload, "expires_at");
        try {
            return EXPIRY.format(Instant.parse(raw));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("outbox payload has unparseable expires_at: " + raw, ex);
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("outbox payload is not valid JSON", ex);
        }
    }

    // Every field here is written by an Argus module, so a missing one is our bug, not the user's.
    // Failing loudly beats sending an email with a dead link in it.
    private static String required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new IllegalArgumentException("outbox payload is missing required field: " + field);
        }
        return value.asString();
    }
}
