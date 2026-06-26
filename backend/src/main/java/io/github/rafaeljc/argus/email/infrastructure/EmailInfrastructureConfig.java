package io.github.rafaeljc.argus.email.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class EmailInfrastructureConfig {

    // Fails fast at startup if the named instance is missing from application.yaml,
    // which is preferable to a NullPointerException on the first poll tick.
    @Bean
    public CircuitBreaker vendorEmailBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("vendor-email");
    }
}
