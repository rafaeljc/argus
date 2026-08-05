package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.SyncDailyCloses;
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunPricesStepTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId USER_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Ticker AAPL = new Ticker("AAPL");

    @Mock
    private EodPipelineRunRepository runs;

    @Mock
    private ActiveUserIds activeUserIds;

    @Mock
    private HeldTickers heldTickers;

    @Mock
    private SyncDailyCloses syncDailyCloses;

    private RunPricesStep runPricesStep;

    @BeforeEach
    void setUp() {
        runPricesStep = new RunPricesStep(runs, activeUserIds, heldTickers, syncDailyCloses, new FixedClock(NOW));
    }

    private static EodPipelineRun pendingRun() {
        return new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void execute_pendingRun_startsInProgressThenSucceedsAndMarksStepSucceeded() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));
        when(heldTickers.findForUserIds(List.of(USER_ID))).thenReturn(Set.of(AAPL));
        when(syncDailyCloses.sync(Set.of(AAPL), LocalDate.of(2026, 6, 22))).thenReturn(1);

        EodPipelineRun result = runPricesStep.execute(RUN_ID);

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.finishedAt()).isNull();
        assertThat(result.errorMessage()).isNull();
        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs, times(2)).update(captor.capture());
        EodPipelineRun startedUpdate = captor.getAllValues().get(0);
        assertThat(startedUpdate.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(startedUpdate.stepPricesStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(startedUpdate.finishedAt()).isNull();
    }

    @Test
    void execute_noActiveUsers_stillSucceedsWithEmptyTickerSet() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of());
        when(heldTickers.findForUserIds(List.of())).thenReturn(Set.of());
        when(syncDailyCloses.sync(Set.of(), LocalDate.of(2026, 6, 22))).thenReturn(0);

        EodPipelineRun result = runPricesStep.execute(RUN_ID);

        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
    }

    @Test
    void execute_syncThrows_marksRunAndStepFailedAndReturnsWithoutThrowing() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));
        when(heldTickers.findForUserIds(anyCollection())).thenReturn(Set.of(AAPL));
        when(syncDailyCloses.sync(Set.of(AAPL), LocalDate.of(2026, 6, 22)))
                .thenThrow(new RuntimeException("vendor 503"));

        EodPipelineRun result = runPricesStep.execute(RUN_ID);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.errorMessage()).isEqualTo("vendor 503");
        verify(runs, times(2)).update(any());
    }

    @Test
    void execute_syncThrowsWithNullMessage_usesExceptionClassNameAsErrorMessage() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));
        when(heldTickers.findForUserIds(anyCollection())).thenReturn(Set.of(AAPL));
        when(syncDailyCloses.sync(Set.of(AAPL), LocalDate.of(2026, 6, 22)))
                .thenThrow(new IllegalStateException());

        EodPipelineRun result = runPricesStep.execute(RUN_ID);

        assertThat(result.errorMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void execute_missingRun_throwsResourceNotFoundException() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runPricesStep.execute(RUN_ID)).isInstanceOf(ResourceNotFoundException.class);

        verify(runs, never()).update(any());
    }

    @Test
    void execute_alreadyInProgressRun_doesNotChangeRunStatusOnStart() {
        EodPipelineRun inProgress = new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(inProgress));
        when(activeUserIds.find()).thenReturn(List.of());
        when(heldTickers.findForUserIds(List.of())).thenReturn(Set.of());
        when(syncDailyCloses.sync(Set.of(), LocalDate.of(2026, 6, 22))).thenReturn(0);

        runPricesStep.execute(RUN_ID);

        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs, times(2)).update(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(RunStatus.IN_PROGRESS);
    }
}
