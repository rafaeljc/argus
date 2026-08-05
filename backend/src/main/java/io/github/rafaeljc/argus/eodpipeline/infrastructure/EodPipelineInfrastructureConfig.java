package io.github.rafaeljc.argus.eodpipeline.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
