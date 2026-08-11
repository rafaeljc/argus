package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobClaimer;
import io.github.rafaeljc.argus.portfolio.domain.RebuildJobStatus;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotRebuildWorkerTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");

    @Mock
    private SnapshotRebuildJobClaimer claimer;

    @Mock
    private RebuildSnapshotHistory rebuildSnapshotHistory;

    private SnapshotRebuildWorker worker;

    @BeforeEach
    void setUp() {
        worker = new SnapshotRebuildWorker(claimer, rebuildSnapshotHistory, new FixedClock(NOW));
    }

    private SnapshotRebuildJob job() {
        return new SnapshotRebuildJob(
                new JobId(UUID.randomUUID()), new UserId(UuidCreator.getTimeOrderedEpoch()),
                RebuildJobStatus.IN_PROGRESS, NOW, NOW, null, null);
    }

    @Test
    void processOnePendingJob_noPendingJob_returnsFalse() {
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.empty());

        boolean result = worker.processOnePendingJob();

        assertThat(result).isFalse();
        verify(rebuildSnapshotHistory, never()).rebuild(any());
    }

    @Test
    void processOnePendingJob_rebuildSucceeds_marksCompleted() {
        SnapshotRebuildJob claimed = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));

        boolean result = worker.processOnePendingJob();

        assertThat(result).isTrue();
        verify(rebuildSnapshotHistory).rebuild(claimed.userId());
        verify(claimer).markCompleted(claimed.id(), NOW);
        verify(claimer, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void processOnePendingJob_rebuildThrows_marksFailedWithMessageAndReturnsTrue() {
        SnapshotRebuildJob claimed = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(rebuildSnapshotHistory).rebuild(claimed.userId());

        boolean result = worker.processOnePendingJob();

        assertThat(result).isTrue();
        verify(claimer).markFailed(claimed.id(), "db down", NOW);
        verify(claimer, never()).markCompleted(any(), any());
    }

    @Test
    void processOnePendingJob_rebuildThrowsWithNullMessage_marksFailedWithExceptionClassName() {
        SnapshotRebuildJob claimed = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(claimed));
        org.mockito.Mockito.doThrow(new RuntimeException())
                .when(rebuildSnapshotHistory).rebuild(claimed.userId());

        worker.processOnePendingJob();

        verify(claimer).markFailed(claimed.id(), "RuntimeException", NOW);
    }

    @Test
    void processPendingBatch_multiplePendingJobs_processesAllUntilEmpty() {
        SnapshotRebuildJob a = job();
        SnapshotRebuildJob b = job();
        when(claimer.claimNextPending(NOW))
                .thenReturn(Optional.of(a))
                .thenReturn(Optional.of(b))
                .thenReturn(Optional.empty());

        worker.processPendingBatch();

        verify(claimer).markCompleted(a.id(), NOW);
        verify(claimer).markCompleted(b.id(), NOW);
        verify(claimer, times(3)).claimNextPending(NOW);
    }

    @Test
    void processPendingBatch_moreJobsThanCap_stopsAtMaxJobsPerTick() {
        SnapshotRebuildJob endless = job();
        when(claimer.claimNextPending(NOW)).thenReturn(Optional.of(endless));

        worker.processPendingBatch();

        verify(claimer, times(SnapshotRebuildWorker.MAX_JOBS_PER_TICK)).claimNextPending(NOW);
    }
}
