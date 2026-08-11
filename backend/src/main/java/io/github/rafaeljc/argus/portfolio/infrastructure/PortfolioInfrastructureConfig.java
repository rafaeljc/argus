package io.github.rafaeljc.argus.portfolio.infrastructure;

import io.github.rafaeljc.argus.portfolio.application.RequeueInterruptedSnapshotRebuildJobs;
import io.github.rafaeljc.argus.portfolio.application.SnapshotRebuildWorker;
import io.github.rafaeljc.argus.portfolio.infrastructure.scheduler.SnapshotRebuildJobReaper;
import io.github.rafaeljc.argus.portfolio.infrastructure.scheduler.SnapshotRebuildScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class PortfolioInfrastructureConfig {

    // Positive profile whitelist rather than a blacklist: scheduled/startup beans must not fire
    // during *IT tests (they'd add background DB writes on shared state) — those run without any
    // of these profiles active, so the bean is simply not registered.
    @Bean
    @Profile({"local", "prod"})
    public SnapshotRebuildScheduler snapshotRebuildScheduler(SnapshotRebuildWorker worker) {
        return new SnapshotRebuildScheduler(worker);
    }

    @Bean
    @Profile({"local", "prod"})
    public SnapshotRebuildJobReaper snapshotRebuildJobReaper(
            RequeueInterruptedSnapshotRebuildJobs requeueInterruptedSnapshotRebuildJobs) {
        return new SnapshotRebuildJobReaper(requeueInterruptedSnapshotRebuildJobs);
    }
}
