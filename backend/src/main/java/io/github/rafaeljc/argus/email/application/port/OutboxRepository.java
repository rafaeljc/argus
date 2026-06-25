package io.github.rafaeljc.argus.email.application.port;

import io.github.rafaeljc.argus.email.domain.OutboxMessage;
import java.time.Instant;
import java.util.List;

public interface OutboxRepository {

    boolean insertIfAbsent(OutboxMessage message);

    List<OutboxMessage> claimUnpublishedBatch(int limit, String workerId, Instant now);
}
