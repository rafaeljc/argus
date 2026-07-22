package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.domain.Holding;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final SnapshotWriter snapshotWriter;
    private final GetSnapshot getSnapshot;
    private final GetActiveHoldings getActiveHoldings;

    public PortfolioService(
            SnapshotWriter snapshotWriter, GetSnapshot getSnapshot, GetActiveHoldings getActiveHoldings) {
        this.snapshotWriter = snapshotWriter;
        this.getSnapshot = getSnapshot;
        this.getActiveHoldings = getActiveHoldings;
    }

    @Transactional
    public void writeSnapshot(UserId userId, LocalDate snapshotDate) {
        snapshotWriter.writeFor(userId, snapshotDate);
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> getPortfolioSnapshot(UserId userId, LocalDate snapshotDate) {
        return getSnapshot.at(userId, snapshotDate);
    }

    @Transactional(readOnly = true)
    public List<Holding> getActivePortfolioHoldings(UserId userId) {
        return getActiveHoldings.forUser(userId);
    }
}
