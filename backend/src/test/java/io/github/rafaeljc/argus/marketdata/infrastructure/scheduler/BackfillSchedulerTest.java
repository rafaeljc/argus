package io.github.rafaeljc.argus.marketdata.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.github.rafaeljc.argus.marketdata.application.BackfillWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackfillSchedulerTest {

    @Mock
    private BackfillWorker worker;

    @Test
    void poll_delegatesToWorkerProcessPendingBatch() {
        BackfillScheduler scheduler = new BackfillScheduler(worker);

        scheduler.poll();

        verify(worker).processPendingBatch();
    }

    @Test
    void poll_swallowsRuntimeExceptionsSoSchedulerThreadKeepsRunning() {
        doThrow(new RuntimeException("boom")).when(worker).processPendingBatch();
        BackfillScheduler scheduler = new BackfillScheduler(worker);

        assertThatCode(scheduler::poll).doesNotThrowAnyException();
    }
}
