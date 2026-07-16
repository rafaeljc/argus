package io.github.rafaeljc.argus.marketdata.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketdataInfrastructureConfig {

    // Fails fast at startup if the named instance is missing from application.yaml,
    // which is preferable to a NullPointerException on the first vendor call.
    @Bean
    public CircuitBreaker vendorMarketdataBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("vendor-marketdata");
    }
}
