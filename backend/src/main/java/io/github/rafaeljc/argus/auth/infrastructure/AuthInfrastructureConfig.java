package io.github.rafaeljc.argus.auth.infrastructure;

import io.github.rafaeljc.argus.auth.application.SweepExpiredSessionsOnce;
import io.github.rafaeljc.argus.auth.infrastructure.scheduler.SessionSweeperScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AuthInfrastructureConfig {

    // Positive profile whitelist rather than a blacklist: scheduled beans must not fire during
    // *IT tests (they'd add background DB writes on shared state) — those run without any of
    // these profiles active, so the bean is simply not registered.
    @Bean
    @Profile({"local", "prod"})
    public SessionSweeperScheduler sessionSweeperScheduler(SweepExpiredSessionsOnce sweepExpiredSessionsOnce) {
        return new SessionSweeperScheduler(sweepExpiredSessionsOnce);
    }
}
