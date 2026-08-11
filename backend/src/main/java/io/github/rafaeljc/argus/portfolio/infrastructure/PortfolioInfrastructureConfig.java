package io.github.rafaeljc.argus.portfolio.infrastructure;

import io.github.rafaeljc.argus.portfolio.application.SnapshotRebuildWorker;
import io.github.rafaeljc.argus.portfolio.infrastructure.scheduler.SnapshotRebuildScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class PortfolioInfrastructureConfig {

    @Bean
    @Profile({"local", "prod"})
    public SnapshotRebuildScheduler snapshotRebuildScheduler(SnapshotRebuildWorker worker) {
        return new SnapshotRebuildScheduler(worker);
    }
}
