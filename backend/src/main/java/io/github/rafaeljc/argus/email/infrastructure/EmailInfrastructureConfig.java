package io.github.rafaeljc.argus.email.infrastructure;

import io.github.rafaeljc.argus.common.infrastructure.AppProperties;
import io.github.rafaeljc.argus.email.application.PollOutboxOnce;
import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.noop.NoOpLoggingEmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.resend.ResendEmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.resend.ResendProperties;
import io.github.rafaeljc.argus.email.infrastructure.scheduler.OutboxPollerScheduler;
import io.github.rafaeljc.argus.email.infrastructure.template.EmailTemplateRenderer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class EmailInfrastructureConfig {

    private static final String VENDOR_PROPERTY = "argus.email.vendor";

    // Fails fast at startup if the named instance is missing from application.yaml,
    // which is preferable to a NullPointerException on the first poll tick.
    @Bean
    public CircuitBreaker vendorEmailBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("vendor-email");
    }

    // The breaker protects Argus from a sick vendor; it does nothing about the ordinary transient
    // failure — a dropped connection, a 502 from a proxy. Those are retried inside the adapter
    // before the breaker ever sees them.
    @Bean
    public Retry vendorEmailRetry(RetryRegistry registry) {
        return registry.retry("vendor-email");
    }

    // Positive profile whitelist rather than a blacklist: scheduled beans must not fire during
    // *IT tests (they'd add background DB writes on shared state) — those run without any of
    // these profiles active, so the bean is simply not registered.
    @Bean
    @Profile({"local", "prod"})
    public OutboxPollerScheduler outboxPollerScheduler(PollOutboxOnce pollOutboxOnce) {
        return new OutboxPollerScheduler(pollOutboxOnce);
    }

    // The two gateway beans are mutually exclusive by construction, so exactly one exists in every
    // environment. The no-op is the default: reaching a real vendor takes an explicit opt-in, so
    // no test run and no local run can mail a real inbox by accident. A vendor name matching
    // neither leaves no gateway at all and fails the boot, which beats silently logging mail that
    // an operator believes is being sent.
    @Bean
    @ConditionalOnProperty(name = VENDOR_PROPERTY, havingValue = "noop", matchIfMissing = true)
    public EmailGateway noOpEmailGateway() {
        return new NoOpLoggingEmailGateway();
    }

    // Nested so the vendor properties are bound only when that vendor is selected. Bound
    // unconditionally, every local run and every test — none of which have credentials — would
    // fail to start.
    //
    // Selected by property rather than by profile (unlike marketdata): sending a real email is the
    // only way to check that templates, links, and sender reputation actually work, and tying that
    // to the `prod` profile would drag in the prod datasource and its credentials to do it.
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = VENDOR_PROPERTY, havingValue = "resend")
    @EnableConfigurationProperties({ResendProperties.class, EmailDeliveryProperties.class, AppProperties.class})
    static class VendorEmailConfig {

        @Bean
        EmailTemplateRenderer emailTemplateRenderer(ObjectMapper objectMapper, AppProperties app) {
            return new EmailTemplateRenderer(objectMapper, app.appBaseUrl());
        }

        @Bean
        EmailGateway resendEmailGateway(
                RestClient.Builder builder,
                ResendProperties properties,
                EmailDeliveryProperties delivery,
                EmailTemplateRenderer renderer,
                Retry vendorEmailRetry) {
            // Timeouts belong on the transport, not in the adapter: without them a stalled vendor
            // connection pins the outbox poller indefinitely, and no message behind it goes out.
            RestClient.Builder configured = builder.requestFactory(ClientHttpRequestFactoryBuilder.detect()
                    .build(HttpClientSettings.defaults()
                            .withTimeouts(properties.connectTimeout(), properties.readTimeout())));
            return new ResendEmailGateway(configured, properties, delivery.address(), renderer, vendorEmailRetry);
        }
    }
}
