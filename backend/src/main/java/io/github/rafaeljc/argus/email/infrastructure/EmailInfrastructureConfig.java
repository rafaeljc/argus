package io.github.rafaeljc.argus.email.infrastructure;

import io.github.rafaeljc.argus.email.application.PollOutboxOnce;
import io.github.rafaeljc.argus.email.infrastructure.scheduler.OutboxPollerScheduler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

    // Registered as a @Bean (not @Component) so @ConditionalOnBean evaluates against a fully
    // built factory — the same condition on a component-scanned class silently excludes the
    // bean because DataSource autoconfig hasn't run yet at scan time. The guard exists so
    // @NoDatabase test contexts (which exclude DataSource autoconfig) can still load.
    @Bean
    @ConditionalOnBean(DataSource.class)
    public OutboxPollerScheduler outboxPollerScheduler(PollOutboxOnce pollOutboxOnce) {
        return new OutboxPollerScheduler(pollOutboxOnce);
    }
}
