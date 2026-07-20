package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.Quantity;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.HoldingRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class HoldingRebuild {

    private final HoldingRepository repository;
    private final Clock clock;

    public HoldingRebuild(HoldingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void apply(UserId userId, Ticker ticker, BigDecimal ledgerNetQuantity) {
        int signum = ledgerNetQuantity.signum();
        if (signum > 0) {
            repository.upsert(userId, ticker, new Quantity(ledgerNetQuantity), clock.now());
            return;
        }
        if (signum == 0) {
            repository.deleteIfPresent(userId, ticker);
        }
    }
}
