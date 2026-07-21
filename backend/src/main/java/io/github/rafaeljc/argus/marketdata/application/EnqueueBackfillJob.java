package io.github.rafaeljc.argus.marketdata.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class EnqueueBackfillJob {

    private final BackfillJobRepository repository;
    private final Clock clock;

    public EnqueueBackfillJob(BackfillJobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void apply(UserId userId, Ticker ticker, LocalDate startDate, LocalDate endDate) {
        BackfillJob job = new BackfillJob(
                new JobId(UuidCreator.getTimeOrderedEpoch()),
                ticker,
                userId,
                JobStatus.PENDING,
                startDate,
                endDate,
                null,
                null,
                clock.now(),
                null,
                null);
        repository.enqueueIfNoActiveJob(job);
    }
}
