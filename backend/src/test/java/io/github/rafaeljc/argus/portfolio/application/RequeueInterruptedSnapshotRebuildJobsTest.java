package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobClaimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequeueInterruptedSnapshotRebuildJobsTest {

    @Mock
    private SnapshotRebuildJobClaimer claimer;

    private RequeueInterruptedSnapshotRebuildJobs requeueInterruptedSnapshotRebuildJobs;

    @BeforeEach
    void setUp() {
        requeueInterruptedSnapshotRebuildJobs = new RequeueInterruptedSnapshotRebuildJobs(claimer);
    }

    @Test
    void execute_jobsLeftInProgress_revertsThemToPending() {
        when(claimer.revertInterruptedJobsToPending()).thenReturn(2);

        assertThat(requeueInterruptedSnapshotRebuildJobs.execute()).isEqualTo(2);
    }

    @Test
    void execute_nothingLeftInProgress_reportsNoRowsAffected() {
        when(claimer.revertInterruptedJobsToPending()).thenReturn(0);

        assertThat(requeueInterruptedSnapshotRebuildJobs.execute()).isZero();
    }
}
