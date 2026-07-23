package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListSnapshots {

    private final PortfolioSnapshotRepository repository;
    private final Clock clock;

    public ListSnapshots(PortfolioSnapshotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<PortfolioSnapshot> list(UserId userId, SnapshotRange range) {
        LocalDate anchor = clock.today().minusDays(1);
        return repository.listByUserAndRange(userId, range.from(anchor), anchor);
    }
}
