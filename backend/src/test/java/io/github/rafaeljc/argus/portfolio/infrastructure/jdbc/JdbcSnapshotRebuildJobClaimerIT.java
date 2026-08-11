package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobClaimer;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobRepository;
import io.github.rafaeljc.argus.portfolio.domain.RebuildJobStatus;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcSnapshotRebuildJobClaimerIT {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private SnapshotRebuildJobClaimer claimer;

    @Autowired
    private SnapshotRebuildJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimNextPending_onePendingJob_claimsAndMarksInProgress() {
        UserId userId = insertUser();
        JobId id = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(id, userId, NOW));

        Optional<SnapshotRebuildJob> claimed = claimer.claimNextPending(NOW);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().id()).isEqualTo(id);
        assertThat(claimed.get().status()).isEqualTo(RebuildJobStatus.IN_PROGRESS);
        assertThat(claimed.get().startedAt()).isEqualTo(NOW);
        assertThat(statusOf(id)).isEqualTo("in_progress");
    }

    @Test
    void claimNextPending_noPendingJobs_returnsEmpty() {
        assertThat(claimer.claimNextPending(NOW)).isEmpty();
    }

    @Test
    void claimNextPending_alreadyClaimedJob_notReturnedAgain() {
        UserId userId = insertUser();
        jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userId, NOW));
        claimer.claimNextPending(NOW);

        assertThat(claimer.claimNextPending(NOW.plusSeconds(1))).isEmpty();
    }

    @Test
    void claimNextPending_multiplePendingJobs_returnsOldestFirst() {
        UserId userA = insertUser();
        UserId userB = insertUser();
        JobId olderId = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(olderId, userA, NOW.minusSeconds(60)));
        jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userB, NOW));

        Optional<SnapshotRebuildJob> claimed = claimer.claimNextPending(NOW.plusSeconds(1));

        assertThat(claimed).isPresent();
        assertThat(claimed.get().id()).isEqualTo(olderId);
    }

    @Test
    void markCompleted_setsStatusAndCompletedAt() {
        UserId userId = insertUser();
        JobId id = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(id, userId, NOW));
        claimer.claimNextPending(NOW);

        claimer.markCompleted(id, NOW.plusSeconds(30));

        assertThat(statusOf(id)).isEqualTo("completed");
    }

    @Test
    void markFailed_setsStatusAndErrorMessage() {
        UserId userId = insertUser();
        JobId id = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(id, userId, NOW));
        claimer.claimNextPending(NOW);

        claimer.markFailed(id, "db down", NOW.plusSeconds(5));

        assertThat(statusOf(id)).isEqualTo("failed");
        String errorMessage = jdbcTemplate.queryForObject(
                "SELECT error_message FROM snapshot_rebuild_jobs WHERE id = ?", String.class, id.value());
        assertThat(errorMessage).isEqualTo("db down");
    }

    @Test
    void revertInterruptedJobsToPending_inProgressJob_revertsStatusAndClearsStartedAt() {
        UserId userId = insertUser();
        JobId id = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(id, userId, NOW));
        claimer.claimNextPending(NOW);

        int reverted = claimer.revertInterruptedJobsToPending();

        assertThat(reverted).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("pending");
        Object startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM snapshot_rebuild_jobs WHERE id = ?", Object.class, id.value());
        assertThat(startedAt).isNull();
    }

    @Test
    void revertInterruptedJobsToPending_thenClaimNextPending_returnsItAgain() {
        UserId userId = insertUser();
        JobId id = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(id, userId, NOW));
        claimer.claimNextPending(NOW);
        claimer.revertInterruptedJobsToPending();

        assertThat(claimer.claimNextPending(NOW.plusSeconds(60))).isPresent();
    }

    @Test
    void revertInterruptedJobsToPending_pendingJob_isLeftUntouched() {
        UserId userId = insertUser();
        JobId id = newJobId();
        jobs.enqueueIfNoActiveJob(pendingJob(id, userId, NOW));

        int reverted = claimer.revertInterruptedJobsToPending();

        assertThat(reverted).isZero();
        assertThat(statusOf(id)).isEqualTo("pending");
    }

    @Test
    void revertInterruptedJobsToPending_noInterruptedJobs_reportsZero() {
        assertThat(claimer.revertInterruptedJobsToPending()).isZero();
    }

    private SnapshotRebuildJob pendingJob(JobId id, UserId userId, Instant requestedAt) {
        return new SnapshotRebuildJob(id, userId, RebuildJobStatus.PENDING, requestedAt, null, null, null);
    }

    private String statusOf(JobId id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM snapshot_rebuild_jobs WHERE id = ?", String.class, id.value());
    }

    private static JobId newJobId() {
        return new JobId(UUID.randomUUID());
    }

    private UserId insertUser() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, is_verified, is_suspended, is_deleted) "
                        + "VALUES (?, ?, ?, TRUE, FALSE, FALSE)",
                id, "snapshot-rebuild-claimer-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
    }
}
