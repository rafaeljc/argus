package io.github.rafaeljc.argus.portfolio.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.github.rafaeljc.argus.portfolio.application.RequeueInterruptedSnapshotRebuildJobs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotRebuildJobReaperTest {

    @Mock
    private RequeueInterruptedSnapshotRebuildJobs requeueInterruptedSnapshotRebuildJobs;

    @Test
    void requeueJobsLeftByPreviousProcess_delegatesToRequeueInterruptedSnapshotRebuildJobs() {
        SnapshotRebuildJobReaper reaper = new SnapshotRebuildJobReaper(requeueInterruptedSnapshotRebuildJobs);

        reaper.requeueJobsLeftByPreviousProcess();

        verify(requeueInterruptedSnapshotRebuildJobs).execute();
    }

    @Test
    void requeueJobsLeftByPreviousProcess_swallowsRuntimeExceptionsSoStartupNeverFails() {
        doThrow(new RuntimeException("boom")).when(requeueInterruptedSnapshotRebuildJobs).execute();
        SnapshotRebuildJobReaper reaper = new SnapshotRebuildJobReaper(requeueInterruptedSnapshotRebuildJobs);

        assertThatCode(reaper::requeueJobsLeftByPreviousProcess).doesNotThrowAnyException();
    }
}
