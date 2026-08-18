package io.github.rafaeljc.argus.eodpipeline.infrastructure;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.eodpipeline.application.FailInterruptedRuns;
import io.github.rafaeljc.argus.eodpipeline.application.TriggerRun;
import io.github.rafaeljc.argus.eodpipeline.infrastructure.scheduler.EodPipelineScheduler;
import io.github.rafaeljc.argus.eodpipeline.infrastructure.scheduler.InterruptedRunReaper;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class EodPipelineInfrastructureConfig {

    // Single-threaded so at most one run executes at a time; the partial unique index on
    // eod_pipeline_runs already prevents more than one active run per date, so this is a
    // mutual-exclusion guarantee, not a throughput knob.
    @Bean
    public TaskExecutor eodPipelineTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("eod-pipeline-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Same 20s as spring.task.scheduling.shutdown.await-termination-period: both must drain
        // inside the 25s lifecycle-phase timeout, so they move together rather than independently.
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    // Positive profile whitelist rather than a blacklist: scheduled beans must not fire during
    // *IT tests (they'd add background DB writes on shared state) — those run without any of
    // these profiles active, so the bean is simply not registered.
    @Bean
    @Profile({"local", "prod"})
    public EodPipelineScheduler eodPipelineScheduler(
            MarketCalendar marketCalendar, TriggerRun triggerRun, Clock clock) {
        return new EodPipelineScheduler(marketCalendar, triggerRun, clock);
    }

    // Same profile whitelist as the scheduler: an *IT context starts and stops repeatedly against a
    // shared database, and this would fail runs other tests had just seeded.
    @Bean
    @Profile({"local", "prod"})
    public InterruptedRunReaper interruptedRunReaper(FailInterruptedRuns failInterruptedRuns) {
        return new InterruptedRunReaper(failInterruptedRuns);
    }
}
