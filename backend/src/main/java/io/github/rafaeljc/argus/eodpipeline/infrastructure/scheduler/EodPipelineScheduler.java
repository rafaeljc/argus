package io.github.rafaeljc.argus.eodpipeline.infrastructure.scheduler;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.eodpipeline.application.TriggerRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class EodPipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(EodPipelineScheduler.class);

    private final MarketCalendar marketCalendar;
    private final TriggerRun triggerRun;
    private final Clock clock;

    public EodPipelineScheduler(MarketCalendar marketCalendar, TriggerRun triggerRun, Clock clock) {
        this.marketCalendar = marketCalendar;
        this.triggerRun = triggerRun;
        this.clock = clock;
    }

    @PostConstruct
    void logStartup() {
        log.info("eod pipeline scheduler started");
    }

    @Scheduled(cron = "${argus.eodpipeline.cron:0 0 17 * * MON-FRI}", zone = "America/New_York")
    public void triggerDailyRun() {
        LocalDate today = clock.today();
        if (!marketCalendar.isTradingDay(today)) {
            log.info("eod pipeline cron skipped: {} is not a trading day", today);
            return;
        }

        try {
            triggerRun.execute(today, Trigger.CRON);
        } catch (RunAlreadyActiveException e) {
            // A manual admin trigger beat the cron to it for today — expected, not an error.
            log.info("eod pipeline cron skipped: run already active for {}", today);
        } catch (RuntimeException e) {
            // Swallow so a single bad run doesn't kill the scheduler thread; next trading day retries.
            log.error("eod pipeline cron trigger failed", e);
        }
    }
}
