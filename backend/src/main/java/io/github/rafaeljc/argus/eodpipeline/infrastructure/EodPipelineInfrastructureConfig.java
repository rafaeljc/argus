package io.github.rafaeljc.argus.eodpipeline.infrastructure;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.eodpipeline.application.TriggerRun;
import io.github.rafaeljc.argus.eodpipeline.infrastructure.scheduler.EodPipelineScheduler;
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
}
