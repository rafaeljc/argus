package io.github.rafaeljc.argus.portfolio.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.JobId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.SnapshotRebuildJobRepository;
import io.github.rafaeljc.argus.portfolio.domain.RebuildJobStatus;
import io.github.rafaeljc.argus.portfolio.domain.SnapshotRebuildJob;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcSnapshotRebuildJobRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private SnapshotRebuildJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void enqueueIfNoActiveJob_noActiveJobForUser_insertsAndReturnsTrue() {
        UserId userId = insertUser();

        boolean inserted = jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userId));

        assertThat(inserted).isTrue();
        assertThat(countJobsForUser(userId)).isEqualTo(1);
    }

    @Test
    void enqueueIfNoActiveJob_pendingJobAlreadyExists_returnsFalseAndDoesNotInsert() {
        UserId userId = insertUser();
        jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userId));

        boolean inserted = jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userId));

        assertThat(inserted).isFalse();
        assertThat(countJobsForUser(userId)).isEqualTo(1);
    }

    @Test
    void enqueueIfNoActiveJob_inProgressJobAlreadyExists_returnsFalseAndDoesNotInsert() {
        UserId userId = insertUser();
        insertJobWithStatus(newJobId(), userId, "in_progress");

        boolean inserted = jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userId));

        assertThat(inserted).isFalse();
        assertThat(countJobsForUser(userId)).isEqualTo(1);
    }

    @Test
    void enqueueIfNoActiveJob_onlyCompletedJobExists_insertsNewOneAnyway() {
        UserId userId = insertUser();
        insertJobWithStatus(newJobId(), userId, "completed");

        boolean inserted = jobs.enqueueIfNoActiveJob(pendingJob(newJobId(), userId));

        assertThat(inserted).isTrue();
        assertThat(countJobsForUser(userId)).isEqualTo(2);
    }

    @Test
    void enqueueIfNoActiveJob_racingCallWithinOneOpenTransaction_secondCallReturnsFalseAndTransactionCommits() {
        UserId userId = insertUser();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        JobId firstId = newJobId();
        JobId secondId = newJobId();

        transactionTemplate.executeWithoutResult(status -> {
            boolean firstInserted = jobs.enqueueIfNoActiveJob(pendingJob(firstId, userId));
            boolean secondInserted = jobs.enqueueIfNoActiveJob(pendingJob(secondId, userId));

            assertThat(firstInserted).isTrue();
            assertThat(secondInserted).isFalse();
        });

        assertThat(countJobsForUser(userId)).isEqualTo(1);
    }

    private SnapshotRebuildJob pendingJob(JobId id, UserId userId) {
        return new SnapshotRebuildJob(id, userId, RebuildJobStatus.PENDING, NOW, null, null, null);
    }

    private void insertJobWithStatus(JobId id, UserId userId, String status) {
        jdbcTemplate.update(
                "INSERT INTO snapshot_rebuild_jobs (id, user_id, status, requested_at) VALUES (?, ?, ?, ?)",
                id.value(), userId.value(), status, java.sql.Timestamp.from(NOW));
    }

    private int countJobsForUser(UserId userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM snapshot_rebuild_jobs WHERE user_id = ?", Integer.class, userId.value());
        return count == null ? 0 : count;
    }

    private static JobId newJobId() {
        return new JobId(UUID.randomUUID());
    }

    private UserId insertUser() {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, is_verified, is_suspended, is_deleted) "
                        + "VALUES (?, ?, ?, TRUE, FALSE, FALSE)",
                id, "snapshot-rebuild-job-" + id + "@example.com", "not-a-real-hash");
        return new UserId(id);
    }
}
