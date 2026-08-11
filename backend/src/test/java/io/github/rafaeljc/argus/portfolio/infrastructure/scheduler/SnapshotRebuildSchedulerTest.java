package io.github.rafaeljc.argus.portfolio.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.github.rafaeljc.argus.portfolio.application.SnapshotRebuildWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotRebuildSchedulerTest {

    @Mock
    private SnapshotRebuildWorker worker;

    @Test
    void poll_delegatesToWorkerProcessPendingBatch() {
        SnapshotRebuildScheduler scheduler = new SnapshotRebuildScheduler(worker);

        scheduler.poll();

        verify(worker).processPendingBatch();
    }

    @Test
    void poll_swallowsRuntimeExceptionsSoSchedulerThreadKeepsRunning() {
        doThrow(new RuntimeException("boom")).when(worker).processPendingBatch();
        SnapshotRebuildScheduler scheduler = new SnapshotRebuildScheduler(worker);

        assertThatCode(scheduler::poll).doesNotThrowAnyException();
    }
}
