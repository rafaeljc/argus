package io.github.rafaeljc.argus.eodpipeline.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.SyncDailyCloses;
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunPricesStepTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId USER_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Ticker AAPL = new Ticker("AAPL");

    @Mock
    private StepLifecycle lifecycle;

    @Mock
    private ActiveUserIds activeUserIds;

    @Mock
    private HeldTickers heldTickers;

    @Mock
    private SyncDailyCloses syncDailyCloses;

    private RunPricesStep runPricesStep;

    @BeforeEach
    void setUp() {
        runPricesStep = new RunPricesStep(
                new StepExecution(lifecycle), activeUserIds, heldTickers, syncDailyCloses);
    }

    private static EodPipelineRun claimedRun() {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING, null);
    }

    @Test
    void execute_heldTickersResolved_syncsThoseClosesForTheRunDate() {
        EodPipelineRun claimed = claimedRun();
        when(lifecycle.claim(RUN_ID, PipelineStep.PRICES)).thenReturn(claimed);
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));
        when(heldTickers.findForUserIds(List.of(USER_ID))).thenReturn(Set.of(AAPL));

        runPricesStep.execute(RUN_ID);

        verify(syncDailyCloses).sync(Set.of(AAPL), RUN_DATE);
        verify(lifecycle).settle(claimed, PipelineStep.PRICES, StepOutcome.success());
    }

    @Test
    void execute_syncThrows_settlesAsFailedCarryingTheVendorMessage() {
        when(lifecycle.claim(RUN_ID, PipelineStep.PRICES)).thenReturn(claimedRun());
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));
        when(heldTickers.findForUserIds(List.of(USER_ID))).thenReturn(Set.of(AAPL));
        doThrow(new RuntimeException("vendor 503")).when(syncDailyCloses).sync(Set.of(AAPL), RUN_DATE);

        runPricesStep.execute(RUN_ID);

        verify(lifecycle).settle(any(), eq(PipelineStep.PRICES), eq(StepOutcome.failure("vendor 503")));
    }
}
