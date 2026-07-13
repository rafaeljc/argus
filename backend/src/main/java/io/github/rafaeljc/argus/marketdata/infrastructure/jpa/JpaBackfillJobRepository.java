package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaBackfillJobRepository implements BackfillJobRepository {

    // Kept in sync with backfill_jobs_ticker_active_uidx (partial unique on the same set).
    private static final List<String> ACTIVE_STATUSES = List.of(
            JobStatus.PENDING.dbValue(),
            JobStatus.IN_PROGRESS.dbValue());

    private final SpringDataBackfillJobJpaRepository jpa;

    JpaBackfillJobRepository(SpringDataBackfillJobJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BackfillJob save(BackfillJob job) {
        BackfillJobJpaEntity persisted = jpa.save(BackfillJobEntityMapper.toEntity(job));
        return BackfillJobEntityMapper.toDomain(persisted);
    }

    @Override
    public Optional<BackfillJob> findById(JobId id) {
        return jpa.findById(id.value()).map(BackfillJobEntityMapper::toDomain);
    }

    @Override
    public Optional<BackfillJob> findActiveByTicker(Ticker ticker) {
        return jpa.findByTickerAndStatusIn(ticker.value(), ACTIVE_STATUSES)
                .map(BackfillJobEntityMapper::toDomain);
    }
}
