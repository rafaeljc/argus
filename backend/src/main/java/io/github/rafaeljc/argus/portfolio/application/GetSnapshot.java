package io.github.rafaeljc.argus.portfolio.application;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.port.PortfolioSnapshotRepository;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetSnapshot {

    private final PortfolioSnapshotRepository repository;

    public GetSnapshot(PortfolioSnapshotRepository repository) {
        this.repository = repository;
    }

    public Optional<PortfolioSnapshot> at(UserId userId, LocalDate snapshotDate) {
        return repository.findByUserAndDate(userId, snapshotDate);
    }
}
