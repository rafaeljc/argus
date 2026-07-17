package io.github.rafaeljc.argus.marketdata.infrastructure;

import io.github.rafaeljc.argus.marketdata.application.BackfillWorker;
import io.github.rafaeljc.argus.marketdata.infrastructure.scheduler.BackfillScheduler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class MarketdataInfrastructureConfig {

    // Fails fast at startup if the named instance is missing from application.yaml,
    // which is preferable to a NullPointerException on the first vendor call.
    @Bean
    public CircuitBreaker vendorMarketdataBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("vendor-marketdata");
    }

    // Positive profile whitelist rather than a blacklist: scheduled beans must not fire during
    // *IT tests (they'd add background DB writes on shared state) — those run without any of
    // these profiles active, so the bean is simply not registered.
    @Bean
    @Profile({"local", "prod"})
    public BackfillScheduler backfillScheduler(BackfillWorker worker) {
        return new BackfillScheduler(worker);
    }
}
