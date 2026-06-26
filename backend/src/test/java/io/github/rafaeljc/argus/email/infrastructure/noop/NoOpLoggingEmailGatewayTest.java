package io.github.rafaeljc.argus.email.infrastructure.noop;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.OutboxId;
import io.github.rafaeljc.argus.email.application.SendResult;
import io.github.rafaeljc.argus.email.domain.EventType;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class NoOpLoggingEmailGatewayTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-22T12:00:00Z");

    private NoOpLoggingEmailGateway gateway;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        gateway = new NoOpLoggingEmailGateway();
        logger = (Logger) LoggerFactory.getLogger(NoOpLoggingEmailGateway.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void send_anyEventType_logsStructuredFieldsAndReturnsSuccess(EventType eventType) {
        UUID aggregateId = UuidCreator.getTimeOrderedEpoch();
        String idempotenceKey = eventType.dbValue() + ":" + aggregateId;
        OutboxMessage message = new OutboxMessage(
                new OutboxId(UuidCreator.getTimeOrderedEpoch()),
                aggregateId,
                eventType,
                "{\"to\":\"alice@example.com\"}",
                idempotenceKey,
                CREATED_AT,
                null,
                0,
                null,
                null);

        SendResult result = gateway.send(message);

        assertThat(result).isEqualTo(new SendResult(true, null));
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String rendered = event.getFormattedMessage();
        assertThat(rendered).contains("eventType=" + eventType.name());
        assertThat(rendered).contains("idempotenceKey=" + idempotenceKey);
        assertThat(rendered).contains("aggregateId=" + aggregateId);
    }
}
