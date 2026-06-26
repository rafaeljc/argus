package io.github.rafaeljc.argus.email.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.application.port.OutboxRepository;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import({PostgresContainer.class, PollOutboxOnceIT.TestStubsConfig.class})
@SpringBootTest
class PollOutboxOnceIT {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final String WORKER_ID = "worker-it";

    @Autowired
    private PollOutboxOnce pollOutboxOnce;

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private CircuitBreaker vendorEmailBreaker;

    @Autowired
    private ProgrammableEmailGateway gateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetCircuitBreakerAndGateway() {
        vendorEmailBreaker.reset();
        gateway.reset();
    }

    private OutboxMessage build(int seq, Instant createdAt) {
        return new OutboxMessage(
                new OutboxId(UuidCreator.getTimeOrderedEpoch()),
                UuidCreator.getTimeOrderedEpoch(),
                EventType.VERIFICATION,
                "{\"seq\":%d}".formatted(seq),
                "verification:%d:%s".formatted(seq, UUID.randomUUID()),
                createdAt, null, 0, null, null);
    }

    private Map<String, Object> selectRow(OutboxId id) {
        return jdbcTemplate.queryForMap("SELECT * FROM outbox WHERE id = ?", id.value());
    }

    @Test
    void pollOnce_breakerForcedOpen_skipsClaimAndLeavesRowsUnpublished() {
        OutboxMessage msg = build(1, NOW);
        repository.insertIfAbsent(msg);
        gateway.respondWith(msg, new SendResult(true, null));
        vendorEmailBreaker.transitionToForcedOpenState();

        pollOutboxOnce.pollOnce(WORKER_ID);

        Map<String, Object> row = selectRow(msg.id());
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("published_by_worker_id")).isNull();
        assertThat(gateway.sendCount()).isZero();
    }

    @Test
    void pollOnce_gatewaySuccess_marksRowPublished() {
        OutboxMessage msg = build(1, NOW);
        repository.insertIfAbsent(msg);
        gateway.respondWith(msg, new SendResult(true, null));

        pollOutboxOnce.pollOnce(WORKER_ID);

        Map<String, Object> row = selectRow(msg.id());
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("published_by_worker_id")).isEqualTo(WORKER_ID);
        assertThat(row.get("error_count")).isEqualTo(0);
    }

    @Test
    void pollOnce_gatewayReturnsFailure_bumpsErrorCountAndKeepsRowUnpublished() {
        OutboxMessage msg = build(1, NOW);
        repository.insertIfAbsent(msg);
        gateway.respondWith(msg, new SendResult(false, "vendor boom"));

        pollOutboxOnce.pollOnce(WORKER_ID);

        Map<String, Object> row = selectRow(msg.id());
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("error_count")).isEqualTo(1);
        assertThat(row.get("last_error")).isEqualTo("vendor boom");
    }

    @Test
    void pollOnce_rowWithMaxErrorCount_isNotClaimed() {
        OutboxMessage msg = build(1, NOW);
        repository.insertIfAbsent(msg);
        jdbcTemplate.update("UPDATE outbox SET error_count = 10 WHERE id = ?", msg.id().value());
        gateway.respondWith(msg, new SendResult(true, null));

        pollOutboxOnce.pollOnce(WORKER_ID);

        assertThat(gateway.sendCount()).isZero();
        Map<String, Object> row = selectRow(msg.id());
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("error_count")).isEqualTo(10);
    }

    @TestConfiguration
    static class TestStubsConfig {
        @Bean
        @Primary
        ProgrammableEmailGateway programmableEmailGateway() {
            return new ProgrammableEmailGateway();
        }
    }

    static final class ProgrammableEmailGateway implements EmailGateway {
        private final Map<OutboxId, SendResult> responses = new HashMap<>();
        private int sendCount;

        void respondWith(OutboxMessage msg, SendResult result) {
            responses.put(msg.id(), result);
        }

        void reset() {
            responses.clear();
            sendCount = 0;
        }

        int sendCount() {
            return sendCount;
        }

        @Override
        public SendResult send(OutboxMessage message) {
            sendCount++;
            SendResult reply = responses.get(message.id());
            if (reply == null) {
                throw new IllegalStateException("no stub registered for " + message.id());
            }
            return reply;
        }
    }
}
