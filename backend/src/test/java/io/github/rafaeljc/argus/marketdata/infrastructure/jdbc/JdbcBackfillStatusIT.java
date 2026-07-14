package io.github.rafaeljc.argus.marketdata.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobRepository;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillStatus;
import io.github.rafaeljc.argus.marketdata.application.port.SymbolRepository;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.Exchange;
import io.github.rafaeljc.argus.marketdata.domain.JobStatus;
import io.github.rafaeljc.argus.marketdata.domain.Symbol;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcBackfillStatusIT {

    private static final Ticker AAPL = new Ticker("AAPL");
    private static final Ticker MSFT = new Ticker("MSFT");
    private static final Ticker GOOG = new Ticker("GOOG");
    private static final Ticker UNKNOWN = new Ticker("ZZZZ");
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final LocalDate START = LocalDate.of(2021, 6, 15);
    private static final LocalDate END = LocalDate.of(2026, 6, 15);

    @Autowired
    private BackfillStatus status;

    @Autowired
    private BackfillJobRepository jobs;

    @Autowired
    private SymbolRepository symbols;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId userId;

    @BeforeEach
    void seedUser() {
        userId = insertUser();
    }

    @Test
    void isPending_pendingJob_returnsTrue() {
        seedSymbol(AAPL);
        jobs.save(jobFor(AAPL, JobStatus.PENDING));

        assertThat(status.isPending(AAPL)).isTrue();
    }

    @Test
    void isPending_inProgressJob_returnsTrue() {
        seedSymbol(AAPL);
        jobs.save(jobFor(AAPL, JobStatus.IN_PROGRESS));

        assertThat(status.isPending(AAPL)).isTrue();
    }

    @Test
    void isPending_completedJob_returnsFalse() {
        seedSymbol(AAPL);
        jobs.save(jobFor(AAPL, JobStatus.COMPLETED));

        assertThat(status.isPending(AAPL)).isFalse();
    }

    @Test
    void isPending_failedJob_returnsFalse() {
        seedSymbol(AAPL);
        jobs.save(jobFor(AAPL, JobStatus.FAILED));

        assertThat(status.isPending(AAPL)).isFalse();
    }

    @Test
    void isPending_noJob_returnsFalse() {
        assertThat(status.isPending(UNKNOWN)).isFalse();
    }

    @Test
    void pendingAmong_emptyInput_returnsEmptySet() {
        assertThat(status.pendingAmong(Set.of())).isEmpty();
    }

    @Test
    void pendingAmong_singleActiveTicker_returnsIt() {
        seedSymbol(AAPL);
        jobs.save(jobFor(AAPL, JobStatus.PENDING));

        assertThat(status.pendingAmong(Set.of(AAPL))).containsExactly(AAPL);
    }

    @Test
    void pendingAmong_mixOfPendingInProgressCompletedFailedAndUnknown_returnsOnlyActive() {
        seedSymbol(AAPL);
        seedSymbol(MSFT);
        seedSymbol(GOOG);
        jobs.save(jobFor(AAPL, JobStatus.PENDING));
        jobs.save(jobFor(MSFT, JobStatus.IN_PROGRESS));
        jobs.save(jobFor(GOOG, JobStatus.COMPLETED));

        Set<Ticker> result = status.pendingAmong(Set.of(AAPL, MSFT, GOOG, UNKNOWN));

        assertThat(result).containsExactlyInAnyOrder(AAPL, MSFT);
    }

    @Test
    void pendingAmong_completedJobPlusNewPendingJobForSameTicker_returnsTickerOnce() {
        seedSymbol(AAPL);
        // A prior run finished, then the ticker was re-queued.
        jobs.save(jobFor(AAPL, JobStatus.COMPLETED));
        jobs.save(jobFor(AAPL, JobStatus.PENDING));

        assertThat(status.pendingAmong(Set.of(AAPL))).containsExactly(AAPL);
    }

    @Test
    void pendingAmong_noneActive_returnsEmptySet() {
        seedSymbol(AAPL);
        jobs.save(jobFor(AAPL, JobStatus.COMPLETED));

        assertThat(status.pendingAmong(Set.of(AAPL))).isEmpty();
    }

    private void seedSymbol(Ticker ticker) {
        symbols.save(new Symbol(ticker, Exchange.NASDAQ, ticker.value() + " Inc.", false, NOW, NOW, NOW));
    }

    private BackfillJob jobFor(Ticker ticker, JobStatus jobStatus) {
        return switch (jobStatus) {
            case PENDING -> new BackfillJob(
                    newJobId(), ticker, userId, JobStatus.PENDING, START, END,
                    null, null, NOW, null, null);
            case IN_PROGRESS -> new BackfillJob(
                    newJobId(), ticker, userId, JobStatus.IN_PROGRESS, START, END,
                    null, null, NOW, NOW, null);
            case COMPLETED -> new BackfillJob(
                    newJobId(), ticker, userId, JobStatus.COMPLETED, START, END,
                    1250, null, NOW, NOW, NOW);
            case FAILED -> new BackfillJob(
                    newJobId(), ticker, userId, JobStatus.FAILED, START, END,
                    null, "vendor timeout", NOW, NOW, NOW);
        };
    }

    private static JobId newJobId() {
        return new JobId(UUID.randomUUID());
    }

    private UserId insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, is_verified, is_suspended, is_deleted) "
                        + "VALUES (?, ?, ?, TRUE, FALSE, FALSE)",
                id, "backfill-status-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
    }
}
