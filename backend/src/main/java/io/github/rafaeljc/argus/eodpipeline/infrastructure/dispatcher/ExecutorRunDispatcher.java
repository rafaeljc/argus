package io.github.rafaeljc.argus.eodpipeline.infrastructure.dispatcher;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.RunAllSteps;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class ExecutorRunDispatcher implements RunDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExecutorRunDispatcher.class);

    private final TaskExecutor executor;
    private final RunAllSteps runAllSteps;

    ExecutorRunDispatcher(@Qualifier("eodPipelineTaskExecutor") TaskExecutor executor, RunAllSteps runAllSteps) {
        this.executor = executor;
        this.runAllSteps = runAllSteps;
    }

    // TriggerRun is @Transactional; a worker thread that started before commit would findById an
    // invisible row. Deferring to afterCommit when a transaction is active avoids that race.
    @Override
    public void dispatch(RunId id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(id);
                }
            });
        } else {
            submit(id);
        }
    }

    // Mirrors BackfillScheduler/OutboxPollerScheduler: swallow so the pool thread survives, and
    // so a run that fails outside a step's own never-throws contract (e.g. the runs.update write
    // itself) doesn't vanish silently — nothing else is watching this Future.
    private void submit(RunId id) {
        executor.execute(() -> {
            try {
                runAllSteps.forRun(id);
            } catch (RuntimeException e) {
                log.error("eod pipeline run failed unexpectedly: runId={}", id.value(), e);
            }
        });
    }
}
