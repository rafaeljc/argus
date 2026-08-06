package io.github.rafaeljc.argus.eodpipeline.infrastructure.dispatcher;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.RunAllSteps;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class ExecutorRunDispatcherTest {

    private static final RunId RUN_ID = new RunId(UUID.randomUUID());

    @Mock
    private RunAllSteps runAllSteps;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void dispatch_noActiveTransaction_submitsImmediately() {
        ExecutorRunDispatcher dispatcher = new ExecutorRunDispatcher(new SyncTaskExecutor(), runAllSteps);

        dispatcher.dispatch(RUN_ID);

        verify(runAllSteps).forRun(RUN_ID);
    }

    @Test
    void dispatch_activeTransaction_deferSubmitUntilAfterCommit() {
        ExecutorRunDispatcher dispatcher = new ExecutorRunDispatcher(new SyncTaskExecutor(), runAllSteps);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(RUN_ID);
        verifyNoInteractions(runAllSteps);

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(runAllSteps).forRun(RUN_ID);
    }

    @Test
    void dispatch_activeTransactionRolledBack_neverSubmits() {
        ExecutorRunDispatcher dispatcher = new ExecutorRunDispatcher(new SyncTaskExecutor(), runAllSteps);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(RUN_ID);
        TransactionSynchronizationManager.clearSynchronization();

        verify(runAllSteps, never()).forRun(RUN_ID);
    }

    @Test
    void dispatch_runAllStepsThrows_isCaughtAndDoesNotPropagate() {
        doThrow(new RuntimeException("boom")).when(runAllSteps).forRun(RUN_ID);
        ExecutorRunDispatcher dispatcher = new ExecutorRunDispatcher(new SyncTaskExecutor(), runAllSteps);

        assertThatCode(() -> dispatcher.dispatch(RUN_ID)).doesNotThrowAnyException();
    }

    @Test
    void dispatchFrom_noActiveTransaction_submitsImmediately() {
        ExecutorRunDispatcher dispatcher = new ExecutorRunDispatcher(new SyncTaskExecutor(), runAllSteps);

        dispatcher.dispatchFrom(RUN_ID, PipelineStep.PRICES);

        verify(runAllSteps).fromStep(RUN_ID, PipelineStep.PRICES);
    }

    @Test
    void dispatchFrom_activeTransaction_deferSubmitUntilAfterCommit() {
        ExecutorRunDispatcher dispatcher = new ExecutorRunDispatcher(new SyncTaskExecutor(), runAllSteps);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchFrom(RUN_ID, PipelineStep.PRICES);
        verifyNoInteractions(runAllSteps);

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(runAllSteps).fromStep(RUN_ID, PipelineStep.PRICES);
    }
}
