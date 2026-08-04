package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.alerts.application.AlertService;
import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.portfolio.application.PortfolioService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WriteSnapshotAndEvaluateAlerts {

    private static final String LOCK_RESOURCE = "eod-evaluate";

    private final TransactionalMutationLock lock;
    private final PortfolioService portfolioService;
    private final AlertService alertService;

    public WriteSnapshotAndEvaluateAlerts(
            TransactionalMutationLock lock, PortfolioService portfolioService, AlertService alertService) {
        this.lock = lock;
        this.portfolioService = portfolioService;
        this.alertService = alertService;
    }

    // REQUIRES_NEW: each user commits or rolls back independently of the caller's fan-out loop,
    // so one user's failure never undoes another's already-committed snapshot/firings.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forUser(UserId userId, LocalDate runDate) {
        lock.acquireResourceForUser(LOCK_RESOURCE, userId);
        portfolioService.writeSnapshot(userId, runDate);
        alertService.evaluateForUser(userId, runDate);
    }
}
