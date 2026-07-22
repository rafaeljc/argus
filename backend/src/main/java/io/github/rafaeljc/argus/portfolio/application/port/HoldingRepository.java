package io.github.rafaeljc.argus.portfolio.application.port;

import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository {

    void upsert(UserId userId, Ticker ticker, Quantity quantity, Instant now);

    void deleteIfPresent(UserId userId, Ticker ticker);

    Optional<Holding> find(UserId userId, Ticker ticker);

    List<Holding> findByUser(UserId userId);
}
