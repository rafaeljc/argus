package io.github.rafaeljc.argus.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotRebuildJobTest {

    private static final JobId JOB_ID = new JobId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId USER_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void constructor_pendingJobWithMinimalFields_isAllowed() {
        SnapshotRebuildJob job =
                new SnapshotRebuildJob(JOB_ID, USER_ID, RebuildJobStatus.PENDING, NOW, null, null, null);

        assertThat(job.id()).isEqualTo(JOB_ID);
        assertThat(job.userId()).isEqualTo(USER_ID);
        assertThat(job.status()).isEqualTo(RebuildJobStatus.PENDING);
        assertThat(job.requestedAt()).isEqualTo(NOW);
        assertThat(job.startedAt()).isNull();
        assertThat(job.completedAt()).isNull();
        assertThat(job.errorMessage()).isNull();
    }

    @Test
    void constructor_completedJobWithAllFields_isAllowed() {
        Instant startedAt = NOW.plusSeconds(5);
        Instant completedAt = NOW.plusSeconds(10);

        SnapshotRebuildJob job = new SnapshotRebuildJob(
                JOB_ID, USER_ID, RebuildJobStatus.COMPLETED, NOW, startedAt, completedAt, null);

        assertThat(job.startedAt()).isEqualTo(startedAt);
        assertThat(job.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(
                        () -> new SnapshotRebuildJob(null, USER_ID, RebuildJobStatus.PENDING, NOW, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(
                        () -> new SnapshotRebuildJob(JOB_ID, null, RebuildJobStatus.PENDING, NOW, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SnapshotRebuildJob(JOB_ID, USER_ID, null, NOW, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullRequestedAt_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                        new SnapshotRebuildJob(JOB_ID, USER_ID, RebuildJobStatus.PENDING, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_startedAtBeforeRequestedAt_throwsIllegalArgument() {
        Instant earlier = NOW.minusSeconds(1);

        assertThatThrownBy(() -> new SnapshotRebuildJob(
                        JOB_ID, USER_ID, RebuildJobStatus.IN_PROGRESS, NOW, earlier, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedAtBeforeStartedAt_throwsIllegalArgument() {
        Instant startedAt = NOW.plusSeconds(10);
        Instant completedAt = NOW.plusSeconds(5);

        assertThatThrownBy(() -> new SnapshotRebuildJob(
                        JOB_ID, USER_ID, RebuildJobStatus.COMPLETED, NOW, startedAt, completedAt, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedAtWithoutStartedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SnapshotRebuildJob(
                        JOB_ID, USER_ID, RebuildJobStatus.COMPLETED, NOW, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
