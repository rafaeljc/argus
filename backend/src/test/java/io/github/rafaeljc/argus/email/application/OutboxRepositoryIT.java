package io.github.rafaeljc.argus.email.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgresContainer.class)
@SpringBootTest
class OutboxRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private OutboxMessage build(int seq, Instant createdAt) {
        return new OutboxMessage(
                new OutboxId(UuidCreator.getTimeOrderedEpoch()),
                UuidCreator.getTimeOrderedEpoch(),
                EventType.VERIFICATION,
                "{\"seq\":%d}".formatted(seq),
                "verification:%d:%s".formatted(seq, UUID.randomUUID()),
                createdAt, null, 0, null, null);
    }

    @Test
    void insert_then_claim_returnsRowStampedWithWorkerId() {
        OutboxMessage msg = build(1, NOW);
        repository.insertIfAbsent(msg);

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(10, "worker-a", NOW.plusSeconds(1));

        assertThat(claimed).hasSize(1);
        OutboxMessage row = claimed.get(0);
        assertThat(row.id()).isEqualTo(msg.id());
        assertThat(row.aggregateId()).isEqualTo(msg.aggregateId());
        assertThat(row.eventType()).isEqualTo(EventType.VERIFICATION);
        // Postgres JSONB normalizes whitespace, so we compare against the round-tripped form.
        assertThat(row.payload()).isEqualTo("{\"seq\": 1}");
        assertThat(row.idempotenceKey()).isEqualTo(msg.idempotenceKey());
        assertThat(row.publishedAt()).isNull();
        assertThat(row.errorCount()).isZero();
        assertThat(row.lastError()).isNull();
        assertThat(row.publishedByWorkerId()).isEqualTo("worker-a");
    }

    @Test
    void claim_respectsLimit_andReturnsOldestFirst() {
        for (int i = 0; i < 5; i++) {
            repository.insertIfAbsent(build(i, NOW.plusSeconds(i)));
        }

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(3, "worker-a", NOW.plusSeconds(10));

        // UPDATE ... RETURNING does not preserve the inner SELECT's ORDER BY, so we assert the
        // set of claimed rows. The ORDER BY exists to pick the OLDEST candidates for the LIMIT.
        assertThat(claimed).hasSize(3);
        assertThat(claimed)
                .extracting(OutboxMessage::createdAt)
                .containsExactlyInAnyOrder(NOW, NOW.plusSeconds(1), NOW.plusSeconds(2));
    }

    @Test
    void claim_skipsAlreadyPublishedByOtherWorker_viaSkipLocked() {
        for (int i = 0; i < 4; i++) {
            repository.insertIfAbsent(build(i, NOW.plusSeconds(i)));
        }
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(outerStatus -> {
            List<OutboxMessage> first = repository.claimUnpublishedBatch(2, "worker-a", NOW.plusSeconds(10));
            List<OutboxMessage> second = inner.execute(innerStatus ->
                    repository.claimUnpublishedBatch(10, "worker-b", NOW.plusSeconds(10)));

            assertThat(first).hasSize(2);
            assertThat(second).hasSize(2);
            Set<OutboxId> firstIds = new HashSet<>(first.stream().map(OutboxMessage::id).toList());
            Set<OutboxId> secondIds = new HashSet<>(second.stream().map(OutboxMessage::id).toList());
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            assertThat(first).allSatisfy(m ->
                    assertThat(m.publishedByWorkerId()).isEqualTo("worker-a"));
            assertThat(second).allSatisfy(m ->
                    assertThat(m.publishedByWorkerId()).isEqualTo("worker-b"));
        });
    }

    @Test
    void claim_skipsRowsCreatedAfterNow() {
        repository.insertIfAbsent(build(0, NOW));
        repository.insertIfAbsent(build(1, NOW.plusSeconds(60)));

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(10, "worker-a", NOW.plusSeconds(1));

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).createdAt()).isEqualTo(NOW);
    }

    @Test
    void insertIfAbsent_duplicateIdempotenceKey_returnsFalseAndPreservesOriginalRow() {
        OutboxMessage first = build(0, NOW);
        assertThat(repository.insertIfAbsent(first)).isTrue();
        OutboxMessage clash = new OutboxMessage(
                new OutboxId(UuidCreator.getTimeOrderedEpoch()),
                UuidCreator.getTimeOrderedEpoch(),
                EventType.PASSWORD_RESET,
                "{\"seq\":99}",
                first.idempotenceKey(),
                NOW, null, 0, null, null);

        assertThat(repository.insertIfAbsent(clash)).isFalse();

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(10, "worker-a", NOW.plusSeconds(60));
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(first.id());
        assertThat(claimed.get(0).eventType()).isEqualTo(EventType.VERIFICATION);
    }

    @Test
    void emailService_enqueue_persistsRowReadableByClaim() {
        UUID aggregateId = UuidCreator.getTimeOrderedEpoch();
        String idempotenceKey = "verification:" + aggregateId;

        emailService.enqueue(EventType.VERIFICATION, aggregateId, "{\"to\":\"a@b.c\"}", idempotenceKey);

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(10, "worker-a", Instant.now().plusSeconds(60));
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).aggregateId()).isEqualTo(aggregateId);
        assertThat(claimed.get(0).idempotenceKey()).isEqualTo(idempotenceKey);
    }

    @Test
    void emailService_duplicateEnqueueInsideCallerTransaction_doesNotPoisonTransaction() {
        UUID aggregateId = UuidCreator.getTimeOrderedEpoch();
        String dupeKey = "verification:" + aggregateId;
        emailService.enqueue(EventType.VERIFICATION, aggregateId, "{\"first\":true}", dupeKey);
        String freshKey = "verification:fresh:" + UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailService.enqueue(EventType.VERIFICATION, aggregateId, "{\"dupe\":true}", dupeKey);
            emailService.enqueue(EventType.PASSWORD_RESET, aggregateId, "{\"after\":true}", freshKey);
        });

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(10, "worker-a", Instant.now().plusSeconds(60));
        assertThat(claimed).hasSize(2);
        assertThat(claimed)
                .extracting(OutboxMessage::idempotenceKey)
                .containsExactlyInAnyOrder(dupeKey, freshKey);
    }

    @Test
    void emailService_enqueueDuplicate_swallowsAndKeepsOriginalRow() {
        UUID aggregateId = UuidCreator.getTimeOrderedEpoch();
        String idempotenceKey = "verification:" + aggregateId;
        emailService.enqueue(EventType.VERIFICATION, aggregateId, "{\"first\":true}", idempotenceKey);

        emailService.enqueue(EventType.VERIFICATION, aggregateId, "{\"second\":true}", idempotenceKey);

        List<OutboxMessage> claimed = repository.claimUnpublishedBatch(10, "worker-a", Instant.now().plusSeconds(60));
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).payload()).isEqualTo("{\"first\": true}");
    }
}
