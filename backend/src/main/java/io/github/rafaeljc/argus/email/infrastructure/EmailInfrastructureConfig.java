package io.github.rafaeljc.argus.email.infrastructure;

import io.github.rafaeljc.argus.email.application.PollOutboxOnce;
import io.github.rafaeljc.argus.email.infrastructure.scheduler.OutboxPollerScheduler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class EmailInfrastructureConfig {

    // Fails fast at startup if the named instance is missing from application.yaml,
    // which is preferable to a NullPointerException on the first poll tick.
    @Bean
    public CircuitBreaker vendorEmailBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("vendor-email");
    }

    // Positive profile whitelist rather than a blacklist: scheduled beans must not fire during
    // *IT tests (they'd add background DB writes on shared state) — those run without any of
    // these profiles active, so the bean is simply not registered.
    @Bean
    @Profile({"local", "prod"})
    public OutboxPollerScheduler outboxPollerScheduler(PollOutboxOnce pollOutboxOnce) {
        return new OutboxPollerScheduler(pollOutboxOnce);
    }
}
