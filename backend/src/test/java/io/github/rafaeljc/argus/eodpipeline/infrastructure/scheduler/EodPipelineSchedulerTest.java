package io.github.rafaeljc.argus.eodpipeline.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.eodpipeline.application.TriggerRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.port.MarketCalendar;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EodPipelineSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-06-22T20:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    @Mock
    private MarketCalendar marketCalendar;

    @Mock
    private TriggerRun triggerRun;

    private EodPipelineScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EodPipelineScheduler(marketCalendar, triggerRun, new FixedClock(NOW));
    }

    @Test
    void triggerDailyRun_notTradingDay_neverCallsTriggerRun() {
        when(marketCalendar.isTradingDay(TODAY)).thenReturn(false);

        scheduler.triggerDailyRun();

        verifyNoInteractions(triggerRun);
    }

    @Test
    void triggerDailyRun_tradingDay_callsTriggerRunWithTodayAndCron() {
        when(marketCalendar.isTradingDay(TODAY)).thenReturn(true);

        scheduler.triggerDailyRun();

        verify(triggerRun).execute(TODAY, Trigger.CRON, null);
    }

    @Test
    void triggerDailyRun_runAlreadyActive_doesNotPropagate() {
        when(marketCalendar.isTradingDay(TODAY)).thenReturn(true);
        doThrow(new RunAlreadyActiveException(TODAY)).when(triggerRun).execute(TODAY, Trigger.CRON, null);

        assertThatCode(() -> scheduler.triggerDailyRun()).doesNotThrowAnyException();
    }

    @Test
    void triggerDailyRun_unexpectedRuntimeException_doesNotPropagate() {
        when(marketCalendar.isTradingDay(TODAY)).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(triggerRun).execute(TODAY, Trigger.CRON, null);

        assertThatCode(() -> scheduler.triggerDailyRun()).doesNotThrowAnyException();
    }
}
