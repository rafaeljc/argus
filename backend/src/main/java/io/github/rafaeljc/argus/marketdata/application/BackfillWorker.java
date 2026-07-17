package io.github.rafaeljc.argus.marketdata.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.marketdata.application.port.BackfillJobClaimer;
import io.github.rafaeljc.argus.marketdata.application.port.PriceHistoryRepository;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.domain.BackfillJob;
import io.github.rafaeljc.argus.marketdata.domain.PriceHistory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BackfillWorker {

    static final int MAX_JOBS_PER_TICK = 25;

    private static final Logger log = LoggerFactory.getLogger(BackfillWorker.class);

    private final BackfillJobClaimer claimer;
    private final VendorPriceGateway gateway;
    private final PriceHistoryRepository priceHistoryRepository;
    private final CircuitBreaker breaker;
    private final Clock clock;

    public BackfillWorker(
            BackfillJobClaimer claimer,
            VendorPriceGateway gateway,
            PriceHistoryRepository priceHistoryRepository,
            CircuitBreaker vendorMarketdataBreaker,
            Clock clock) {
        this.claimer = claimer;
        this.gateway = gateway;
        this.priceHistoryRepository = priceHistoryRepository;
        this.breaker = vendorMarketdataBreaker;
        this.clock = clock;
    }

    public void processPendingBatch() {
        for (int i = 0; i < MAX_JOBS_PER_TICK; i++) {
            if (!processOnePendingJob()) {
                return;
            }
        }
    }

    boolean processOnePendingJob() {
        CircuitBreaker.State state = breaker.getState();
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            log.info("backfill worker skipped: breaker is {}", state);
            return false;
        }
        Optional<BackfillJob> claimed = claimer.claimNextPending(clock.now());
        if (claimed.isEmpty()) {
            return false;
        }
        return processClaimedJob(claimed.get());
    }

    private boolean processClaimedJob(BackfillJob job) {
        try {
            List<PriceHistory> prices = breaker.executeSupplier(
                    () -> gateway.fetchPriceHistory(job.ticker(), job.startDate(), job.endDate()));
            priceHistoryRepository.upsertBatch(prices);
            claimer.markCompleted(job.id(), prices.size(), clock.now());
            return true;
        } catch (CallNotPermittedException e) {
            log.info("backfill worker: breaker opened mid-call, reverting job {} to pending", job.id());
            claimer.revertToPending(job.id());
            return false;
        } catch (RuntimeException e) {
            String msg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            log.warn("backfill worker: vendor failed for job {}: {}", job.id(), msg);
            claimer.markFailed(job.id(), msg, clock.now());
            return true;
        }
    }
}
