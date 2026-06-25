package io.github.rafaeljc.argus.email.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.OutboxId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

    private static final OutboxId ID = new OutboxId(UuidCreator.getTimeOrderedEpoch());
    private static final UUID AGGREGATE_ID = UuidCreator.getTimeOrderedEpoch();
    private static final EventType TYPE = EventType.VERIFICATION;
    private static final String PAYLOAD = "{\"to\":\"alice@example.com\"}";
    private static final String IDEMPOTENCE_KEY = "verification:" + AGGREGATE_ID;
    private static final Instant CREATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    private static OutboxMessage freshUnpublished() {
        return new OutboxMessage(ID, AGGREGATE_ID, TYPE, PAYLOAD, IDEMPOTENCE_KEY,
                CREATED_AT, null, 0, null, null);
    }

    @Test
    void construct_validFreshRow_exposesAllFields() {
        OutboxMessage msg = freshUnpublished();

        assertThat(msg.id()).isEqualTo(ID);
        assertThat(msg.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(msg.eventType()).isEqualTo(TYPE);
        assertThat(msg.payload()).isEqualTo(PAYLOAD);
        assertThat(msg.idempotenceKey()).isEqualTo(IDEMPOTENCE_KEY);
        assertThat(msg.createdAt()).isEqualTo(CREATED_AT);
        assertThat(msg.publishedAt()).isNull();
        assertThat(msg.errorCount()).isZero();
        assertThat(msg.lastError()).isNull();
        assertThat(msg.publishedByWorkerId()).isNull();
    }

    @Test
    void construct_claimedRow_preservesWorkerIdAndErrorContext() {
        Instant publishedAt = CREATED_AT.plusSeconds(5);
        OutboxMessage msg = new OutboxMessage(ID, AGGREGATE_ID, TYPE, PAYLOAD, IDEMPOTENCE_KEY,
                CREATED_AT, publishedAt, 2, "vendor timeout", "worker-7");

        assertThat(msg.publishedAt()).isEqualTo(publishedAt);
        assertThat(msg.errorCount()).isEqualTo(2);
        assertThat(msg.lastError()).isEqualTo("vendor timeout");
        assertThat(msg.publishedByWorkerId()).isEqualTo("worker-7");
    }

    @Test
    void construct_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(null, AGGREGATE_ID, TYPE, PAYLOAD,
                IDEMPOTENCE_KEY, CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void construct_nullAggregateId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, null, TYPE, PAYLOAD,
                IDEMPOTENCE_KEY, CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void construct_nullEventType_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, null, PAYLOAD,
                IDEMPOTENCE_KEY, CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void construct_nullPayload_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, TYPE, null,
                IDEMPOTENCE_KEY, CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void construct_blankPayload_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, TYPE, "   ",
                IDEMPOTENCE_KEY, CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void construct_nullIdempotenceKey_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, TYPE, PAYLOAD,
                null, CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotenceKey");
    }

    @Test
    void construct_blankIdempotenceKey_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, TYPE, PAYLOAD,
                "  ", CREATED_AT, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotenceKey");
    }

    @Test
    void construct_nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, TYPE, PAYLOAD,
                IDEMPOTENCE_KEY, null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void construct_negativeErrorCount_throwsIllegalArgument() {
        assertThatThrownBy(() -> new OutboxMessage(ID, AGGREGATE_ID, TYPE, PAYLOAD,
                IDEMPOTENCE_KEY, CREATED_AT, null, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCount");
    }
}
