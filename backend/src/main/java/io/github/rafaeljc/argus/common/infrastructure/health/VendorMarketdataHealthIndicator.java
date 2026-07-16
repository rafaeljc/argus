package io.github.rafaeljc.argus.common.infrastructure.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("vendorMarketdata")
public class VendorMarketdataHealthIndicator implements HealthIndicator {

    private final CircuitBreaker breaker;

    public VendorMarketdataHealthIndicator(CircuitBreaker vendorMarketdataBreaker) {
        this.breaker = vendorMarketdataBreaker;
    }

    @Override
    public Health health() {
        CircuitBreaker.State state = breaker.getState();
        Health.Builder builder = isDown(state) ? Health.down() : Health.up();
        return builder
                .withDetail("state", state.name())
                .withDetail("failureRate", breaker.getMetrics().getFailureRate())
                .build();
    }

    private static boolean isDown(CircuitBreaker.State state) {
        return state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN;
    }
}
