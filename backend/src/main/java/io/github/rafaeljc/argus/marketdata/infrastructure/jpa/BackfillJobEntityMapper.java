package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;

final class BackfillJobEntityMapper {

    private BackfillJobEntityMapper() {
    }

    static BackfillJob toDomain(BackfillJobJpaEntity entity) {
        return new BackfillJob(
                new JobId(entity.getId()),
                new Ticker(entity.getTicker()),
                new UserId(entity.getUserId()),
                JobStatus.fromDbValue(entity.getStatus()),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getPriceCount(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt());
    }

    static BackfillJobJpaEntity toEntity(BackfillJob job) {
        return new BackfillJobJpaEntity(
                job.id().value(),
                job.ticker().value(),
                job.userId().value(),
                job.status().dbValue(),
                job.startDate(),
                job.endDate(),
                job.priceCount(),
                job.errorMessage(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt());
    }
}
