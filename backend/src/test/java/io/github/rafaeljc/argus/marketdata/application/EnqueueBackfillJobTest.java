package io.github.rafaeljc.argus.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnqueueBackfillJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final UserId USER_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final Ticker TICKER = new Ticker("AAPL");
    private static final LocalDate START = LocalDate.parse("2021-07-01");
    private static final LocalDate END = LocalDate.parse("2026-07-01");

    @Mock
    private BackfillJobRepository repository;

    private FixedClock clock;
    private EnqueueBackfillJob enqueue;

    @BeforeEach
    void setUp() {
        clock = new FixedClock(FIXED_NOW);
        enqueue = new EnqueueBackfillJob(repository, clock);
    }

    @Test
    void apply_buildsPendingJobAndDelegatesToEnqueueIfNoActiveJob() {
        enqueue.apply(USER_ID, TICKER, START, END);

        ArgumentCaptor<BackfillJob> captor = ArgumentCaptor.forClass(BackfillJob.class);
        verify(repository).enqueueIfNoActiveJob(captor.capture());
        BackfillJob job = captor.getValue();
        assertThat(job.ticker()).isEqualTo(TICKER);
        assertThat(job.userId()).isEqualTo(USER_ID);
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        assertThat(job.startDate()).isEqualTo(START);
        assertThat(job.endDate()).isEqualTo(END);
        assertThat(job.createdAt()).isEqualTo(FIXED_NOW);
    }
}
