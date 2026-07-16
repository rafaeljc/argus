package io.github.rafaeljc.argus.common.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class VendorMarketdataHealthIndicatorTest {

    private CircuitBreaker breaker;
    private VendorMarketdataHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.of("vendor-marketdata", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .build());
        indicator = new VendorMarketdataHealthIndicator(breaker);
    }

    @Test
    void closed_isUp() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("state", "CLOSED");
        assertThat(health.getDetails()).containsKey("failureRate");
    }

    @Test
    void halfOpen_isUp() {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("state", "HALF_OPEN");
    }

    @Test
    void open_isDown() {
        breaker.transitionToOpenState();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("state", "OPEN");
    }

    @Test
    void forcedOpen_isDown() {
        breaker.transitionToForcedOpenState();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("state", "FORCED_OPEN");
    }
}
