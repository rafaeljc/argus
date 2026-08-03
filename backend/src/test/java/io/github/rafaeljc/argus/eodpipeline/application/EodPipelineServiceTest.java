package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.marketdata.application.SyncSymbolUniverse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EodPipelineServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private EodPipelineRunRepository runs;

    @Mock
    private SyncSymbolUniverse syncSymbolUniverse;

    private EodPipelineService service;

    @BeforeEach
    void setUp() {
        service = new EodPipelineService(runs, syncSymbolUniverse, new FixedClock(NOW));
    }

    private EodPipelineRun pendingRun() {
        return new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void runSymbols_pendingRun_startsInProgressThenSucceedsAndMarksStepSucceeded() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));

        EodPipelineRun result = service.runSymbols(RUN_ID);

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.finishedAt()).isNull();
        assertThat(result.errorMessage()).isNull();
        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs, times(2)).update(captor.capture());
        EodPipelineRun startedUpdate = captor.getAllValues().get(0);
        assertThat(startedUpdate.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(startedUpdate.stepSymbolsStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(startedUpdate.finishedAt()).isNull();
    }

    @Test
    void runSymbols_syncThrows_marksRunAndStepFailedAndReturnsWithoutThrowing() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(syncSymbolUniverse.sync()).thenThrow(new RuntimeException("vendor 503"));

        EodPipelineRun result = service.runSymbols(RUN_ID);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.errorMessage()).isEqualTo("vendor 503");
        verify(runs, times(2)).update(any());
    }

    @Test
    void runSymbols_syncThrowsWithNullMessage_usesExceptionClassNameAsErrorMessage() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(syncSymbolUniverse.sync()).thenThrow(new IllegalStateException());

        EodPipelineRun result = service.runSymbols(RUN_ID);

        assertThat(result.errorMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void runSymbols_missingRun_throwsResourceNotFoundException() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runSymbols(RUN_ID)).isInstanceOf(ResourceNotFoundException.class);

        verify(runs, never()).update(any());
    }

    @Test
    void runSymbols_alreadyInProgressRun_doesNotChangeRunStatusOnStart() {
        EodPipelineRun inProgress = new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(inProgress));

        service.runSymbols(RUN_ID);

        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs, times(2)).update(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(RunStatus.IN_PROGRESS);
    }
}
