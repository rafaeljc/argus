package io.github.rafaeljc.argus.marketdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackfillJobTest {

    private static final JobId JOB_ID = new JobId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Ticker AAPL = new Ticker("AAPL");
    private static final UserId USER_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final LocalDate START = LocalDate.of(2021, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 1);
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void constructor_pendingJobWithMinimalFields_isAllowed() {
        BackfillJob job = new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.PENDING, START, END,
                null, null, NOW, null, null);

        assertThat(job.id()).isEqualTo(JOB_ID);
        assertThat(job.ticker()).isEqualTo(AAPL);
        assertThat(job.userId()).isEqualTo(USER_ID);
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        assertThat(job.startDate()).isEqualTo(START);
        assertThat(job.endDate()).isEqualTo(END);
        assertThat(job.priceCount()).isNull();
        assertThat(job.errorMessage()).isNull();
        assertThat(job.createdAt()).isEqualTo(NOW);
        assertThat(job.startedAt()).isNull();
        assertThat(job.completedAt()).isNull();
    }

    @Test
    void constructor_completedJobWithAllFields_isAllowed() {
        Instant startedAt = NOW.plusSeconds(10);
        Instant completedAt = NOW.plusSeconds(30);

        BackfillJob job = new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.COMPLETED, START, END,
                1250, null, NOW, startedAt, completedAt);

        assertThat(job.priceCount()).isEqualTo(1250);
        assertThat(job.startedAt()).isEqualTo(startedAt);
        assertThat(job.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void constructor_startDateEqualsEndDate_isAllowed() {
        BackfillJob job = new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.PENDING, START, START,
                null, null, NOW, null, null);

        assertThat(job.startDate()).isEqualTo(job.endDate());
    }

    @Test
    void constructor_startDateAfterEndDate_throwsIllegalArgument() {
        LocalDate later = END.plusDays(1);

        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.PENDING, later, END,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                null, AAPL, USER_ID, JobStatus.PENDING, START, END,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullTicker_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, null, USER_ID, JobStatus.PENDING, START, END,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, null, JobStatus.PENDING, START, END,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, null, START, END,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStartDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.PENDING, null, END,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullEndDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.PENDING, START, null,
                null, null, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.PENDING, START, END,
                null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativePriceCount_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.COMPLETED, START, END,
                -1, null, NOW, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_startedAtBeforeCreatedAt_throwsIllegalArgument() {
        Instant earlier = NOW.minusSeconds(1);

        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.IN_PROGRESS, START, END,
                null, null, NOW, earlier, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedAtBeforeStartedAt_throwsIllegalArgument() {
        Instant startedAt = NOW.plusSeconds(10);
        Instant completedAt = NOW.plusSeconds(5);

        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.COMPLETED, START, END,
                0, null, NOW, startedAt, completedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedAtEqualsStartedAt_isAllowed() {
        Instant sameMoment = NOW.plusSeconds(10);

        BackfillJob job = new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.COMPLETED, START, END,
                0, null, NOW, sameMoment, sameMoment);

        assertThat(job.completedAt()).isEqualTo(job.startedAt());
    }

    @Test
    void constructor_completedAtWithoutStartedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new BackfillJob(
                JOB_ID, AAPL, USER_ID, JobStatus.COMPLETED, START, END,
                0, null, NOW, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
