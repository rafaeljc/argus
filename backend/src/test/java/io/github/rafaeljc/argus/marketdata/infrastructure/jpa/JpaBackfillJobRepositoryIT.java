package io.github.rafaeljc.argus.marketdata.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JpaBackfillJobRepositoryIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final LocalDate START = LocalDate.of(2021, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 1);
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private BackfillJobRepository jobs;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId userId;

    @BeforeEach
    void seedForeignKeyDependencies() {
        symbols.save(new Symbol(AAPL, Exchange.NASDAQ, "Apple Inc.", false, NOW, NOW, NOW));
        userId = insertUser();
    }

    @Test
    void save_thenFindById_roundTripsAllFields() {
        BackfillJob saved = jobs.save(pendingJob(newJobId()));

        BackfillJob found = jobs.findById(saved.id()).orElseThrow();

        assertThat(found).isEqualTo(saved);
    }

    @Test
    void save_completedJob_persistsAllOptionalFields() {
        Instant startedAt = NOW.plusSeconds(10);
        Instant completedAt = NOW.plusSeconds(30);
        JobId id = newJobId();

        BackfillJob saved = jobs.save(new BackfillJob(
                id, AAPL, userId, JobStatus.COMPLETED, START, END,
                1250, null, NOW, startedAt, completedAt));

        BackfillJob found = jobs.findById(saved.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(found.priceCount()).isEqualTo(1250);
        assertThat(found.startedAt()).isEqualTo(startedAt);
        assertThat(found.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void save_failedJob_persistsErrorMessage() {
        Instant startedAt = NOW.plusSeconds(1);
        Instant completedAt = NOW.plusSeconds(2);
        JobId id = newJobId();
        jobs.save(new BackfillJob(
                id, AAPL, userId, JobStatus.FAILED, START, END,
                null, "vendor 503", NOW, startedAt, completedAt));

        BackfillJob found = jobs.findById(id).orElseThrow();

        assertThat(found.status()).isEqualTo(JobStatus.FAILED);
        assertThat(found.errorMessage()).isEqualTo("vendor 503");
    }

    @Test
    void findActiveByTicker_failedJob_returnsEmpty() {
        Instant startedAt = NOW.plusSeconds(1);
        jobs.save(new BackfillJob(
                newJobId(), AAPL, userId, JobStatus.FAILED, START, END,
                null, "vendor 503", NOW, startedAt, startedAt));

        assertThat(jobs.findActiveByTicker(AAPL)).isEmpty();
    }

    @Test
    void findById_missing_returnsEmpty() {
        assertThat(jobs.findById(newJobId())).isEmpty();
    }

    @Test
    void findActiveByTicker_pendingJob_returnsIt() {
        BackfillJob saved = jobs.save(pendingJob(newJobId()));

        assertThat(jobs.findActiveByTicker(AAPL)).contains(saved);
    }

    @Test
    void findActiveByTicker_inProgressJob_returnsIt() {
        JobId id = newJobId();
        BackfillJob saved = jobs.save(new BackfillJob(
                id, AAPL, userId, JobStatus.IN_PROGRESS, START, END,
                null, null, NOW, NOW.plusSeconds(1), null));

        assertThat(jobs.findActiveByTicker(AAPL)).contains(saved);
    }

    @Test
    void findActiveByTicker_completedJob_returnsEmpty() {
        Instant startedAt = NOW.plusSeconds(1);
        jobs.save(new BackfillJob(
                newJobId(), AAPL, userId, JobStatus.COMPLETED, START, END,
                0, null, NOW, startedAt, startedAt));

        assertThat(jobs.findActiveByTicker(AAPL)).isEmpty();
    }

    @Test
    void findActiveByTicker_noJobs_returnsEmpty() {
        assertThat(jobs.findActiveByTicker(AAPL)).isEmpty();
    }

    @Test
    void save_secondActiveJobForSameTicker_isRejectedByUniqueIndex() {
        jobs.save(pendingJob(newJobId()));

        BackfillJob duplicate = pendingJob(newJobId());

        assertThatThrownBy(() -> jobs.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BackfillJob pendingJob(JobId id) {
        return new BackfillJob(
                id, AAPL, userId, JobStatus.PENDING, START, END,
                null, null, NOW, null, null);
    }

    private static JobId newJobId() {
        return new JobId(UUID.randomUUID());
    }

    private UserId insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, is_verified, is_suspended, is_deleted) "
                        + "VALUES (?, ?, ?, TRUE, FALSE, FALSE)",
                id, "backfill-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
    }
}
