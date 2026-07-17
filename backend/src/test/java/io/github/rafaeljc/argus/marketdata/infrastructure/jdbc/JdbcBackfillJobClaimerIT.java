package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobClaimer;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcBackfillJobClaimerIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final LocalDate START = LocalDate.of(2021, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 1);
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private BackfillJobClaimer claimer;

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
        symbols.save(new Symbol(MSFT, Exchange.NASDAQ, "Microsoft Corp.", false, NOW, NOW, NOW));
        userId = insertUser();
    }

    @Test
    void claimNextPending_onePendingJob_claimsAndMarksInProgress() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));

        Optional<BackfillJob> claimed = claimer.claimNextPending(NOW);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().id()).isEqualTo(saved.id());
        assertThat(claimed.get().status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(claimed.get().startedAt()).isEqualTo(NOW);
        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(reloaded.startedAt()).isEqualTo(NOW);
    }

    @Test
    void claimNextPending_noPendingJobs_returnsEmpty() {
        assertThat(claimer.claimNextPending(NOW)).isEmpty();
    }

    @Test
    void claimNextPending_alreadyClaimedJob_notReturnedAgain() {
        jobs.save(pendingJob(AAPL));
        claimer.claimNextPending(NOW);

        assertThat(claimer.claimNextPending(NOW.plusSeconds(1))).isEmpty();
    }

    @Test
    void claimNextPending_multiplePendingJobs_returnsOldestFirst() {
        BackfillJob older = jobs.save(new BackfillJob(
                newJobId(), AAPL, userId, JobStatus.PENDING, START, END,
                null, null, NOW.minusSeconds(60), null, null));
        jobs.save(new BackfillJob(
                newJobId(), MSFT, userId, JobStatus.PENDING, START, END,
                null, null, NOW, null, null));

        Optional<BackfillJob> claimed = claimer.claimNextPending(NOW.plusSeconds(1));

        assertThat(claimed).isPresent();
        assertThat(claimed.get().id()).isEqualTo(older.id());
    }

    @Test
    void markCompleted_setsStatusPriceCountAndCompletedAt() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));
        claimer.claimNextPending(NOW);

        claimer.markCompleted(saved.id(), 1250, NOW.plusSeconds(30));

        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(reloaded.priceCount()).isEqualTo(1250);
        assertThat(reloaded.completedAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void markFailed_setsStatusErrorMessageAndCompletedAt() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));
        claimer.claimNextPending(NOW);

        claimer.markFailed(saved.id(), "vendor 503", NOW.plusSeconds(5));

        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.FAILED);
        assertThat(reloaded.errorMessage()).isEqualTo("vendor 503");
        assertThat(reloaded.completedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void revertToPending_clearsStartedAtAndResetsStatus() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));
        claimer.claimNextPending(NOW);

        claimer.revertToPending(saved.id());

        BackfillJob reloaded = jobs.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(JobStatus.PENDING);
        assertThat(reloaded.startedAt()).isNull();
    }

    @Test
    void revertToPending_thenClaimNextPending_returnsItAgain() {
        BackfillJob saved = jobs.save(pendingJob(AAPL));
        claimer.claimNextPending(NOW);
        claimer.revertToPending(saved.id());

        Optional<BackfillJob> reclaimed = claimer.claimNextPending(NOW.plusSeconds(60));

        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.get().id()).isEqualTo(saved.id());
    }

    private BackfillJob pendingJob(Ticker ticker) {
        return new BackfillJob(
                newJobId(), ticker, userId, JobStatus.PENDING, START, END,
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
                id, "backfill-claimer-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
    }
}
