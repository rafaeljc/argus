package io.github.rafaeljc.argus.portfolio.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobRepository;
import io.github.rafaeljc.argus.portfolio.domain.RebuildJobStatus;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import org.springframework.stereotype.Service;

@Service
public class EnqueueSnapshotRebuild {

    private final SnapshotRebuildJobRepository repository;
    private final Clock clock;

    public EnqueueSnapshotRebuild(SnapshotRebuildJobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void apply(UserId userId) {
        SnapshotRebuildJob job = new SnapshotRebuildJob(
                new JobId(UuidCreator.getTimeOrderedEpoch()),
                userId,
                RebuildJobStatus.PENDING,
                clock.now(),
                null,
                null,
                null);
        repository.enqueueIfNoActiveJob(job);
    }
}
