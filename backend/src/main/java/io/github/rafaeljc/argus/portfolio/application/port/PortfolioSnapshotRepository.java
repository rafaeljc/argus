package io.github.rafaeljc.argus.portfolio.application.port;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.domain.PortfolioSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository {

    void insertIfAbsent(PortfolioSnapshot snapshot);

    Optional<PortfolioSnapshot> findByUserAndDate(UserId userId, LocalDate snapshotDate);

    List<PortfolioSnapshot> listByUserAndRange(UserId userId, LocalDate from, LocalDate to, int page, int perPage);
}
