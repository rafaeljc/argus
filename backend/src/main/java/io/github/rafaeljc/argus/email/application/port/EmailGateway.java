package io.github.rafaeljc.argus.email.application.port;

import io.github.rafaeljc.argus.email.application.SendResult;
import io.github.rafaeljc.argus.email.domain.OutboxMessage;

public interface EmailGateway {

    SendResult send(OutboxMessage message);
}
