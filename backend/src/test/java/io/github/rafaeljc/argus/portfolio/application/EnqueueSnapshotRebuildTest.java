package io.github.rafaeljc.argus.portfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobRepository;
import io.github.rafaeljc.argus.portfolio.domain.RebuildJobStatus;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnqueueSnapshotRebuildTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private SnapshotRebuildJobRepository repository;

    private FixedClock clock;
    private EnqueueSnapshotRebuild enqueue;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        enqueue = new EnqueueSnapshotRebuild(repository, clock);
    }

    @Test
    void apply_buildsPendingJobAndDelegatesToEnqueueIfNoActiveJob() {
        enqueue.apply(USER_ID);

        ArgumentCaptor<SnapshotRebuildJob> captor = ArgumentCaptor.forClass(SnapshotRebuildJob.class);
        verify(repository).enqueueIfNoActiveJob(captor.capture());
        SnapshotRebuildJob job = captor.getValue();
        assertThat(job.userId()).isEqualTo(USER_ID);
        assertThat(job.status()).isEqualTo(RebuildJobStatus.PENDING);
        assertThat(job.requestedAt()).isEqualTo(FIXED_NOW);
    }
}
