package io.github.rafaeljc.argus.email.infrastructure.noop;

import io.github.rafaeljc.argus.email.application.SendResult;
import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Registered as a @Bean by EmailInfrastructureConfig rather than component-scanned, so that it and
// the real vendor adapter stay mutually exclusive.
public class NoOpLoggingEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(NoOpLoggingEmailGateway.class);

    @Override
    public SendResult send(OutboxMessage message) {
        log.info(
                "vendor email no-op send: eventType={} idempotenceKey={} aggregateId={}",
                message.eventType(),
                message.idempotenceKey(),
                message.aggregateId());
        return new SendResult(true, null);
    }
}
