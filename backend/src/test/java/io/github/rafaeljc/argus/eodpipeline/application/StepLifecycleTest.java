package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepInProgressException;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
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
class StepLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private EodPipelineRunRepository runs;

    private StepLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new StepLifecycle(runs, new FixedClock(NOW));
    }

    private static EodPipelineRun run(RunStatus status, StepStatus symbols, StepStatus prices, StepStatus evaluate) {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, status, NOW, null, symbols, prices, evaluate, null);
    }

    @Test
    void claim_quietRun_writesTheStepInProgressThroughTheGuardedUpdate() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(
                run(RunStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING)));
        when(runs.updateIfNoStepInProgress(any())).thenAnswer(i -> Optional.of(i.getArgument(0)));

        EodPipelineRun claimed = lifecycle.claim(RUN_ID, PipelineStep.PRICES);

        assertThat(claimed.stepPricesStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(claimed.status()).isEqualTo(RunStatus.IN_PROGRESS);
        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs).updateIfNoStepInProgress(captor.capture());
        assertThat(captor.getValue()).isEqualTo(claimed);
    }

    @Test
    void claim_missingRun_throwsResourceNotFoundAndNeverWrites() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lifecycle.claim(RUN_ID, PipelineStep.PRICES))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(runs, never()).updateIfNoStepInProgress(any());
    }

    @Test
    void claim_guardRejects_throwsStepInProgressNamingTheStepThatHoldsTheRun() {
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(
                        run(RunStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING)))
                .thenReturn(Optional.of(
                        run(RunStatus.IN_PROGRESS, StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING)));
        when(runs.updateIfNoStepInProgress(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lifecycle.claim(RUN_ID, PipelineStep.PRICES))
                .isInstanceOf(StepInProgressException.class)
                .extracting("runId", "step")
                .containsExactly(RUN_ID, PipelineStep.SYMBOLS);
    }

    @Test
    void claim_guardRejectsAndTheHolderHasSinceReleased_namesTheAttemptedStep() {
        EodPipelineRun quiet =
                run(RunStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(quiet));
        when(runs.updateIfNoStepInProgress(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lifecycle.claim(RUN_ID, PipelineStep.PRICES))
                .isInstanceOf(StepInProgressException.class)
                .extracting("step")
                .isEqualTo(PipelineStep.PRICES);
    }

    @Test
    void settle_successfulOutcome_marksTheStepSucceededAndLeavesTheRunInProgress() {
        EodPipelineRun claimed =
                run(RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING);
        when(runs.update(any())).thenAnswer(i -> i.getArgument(0));

        EodPipelineRun settled = lifecycle.settle(claimed, PipelineStep.PRICES, StepOutcome.success());

        assertThat(settled.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(settled.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(settled.finishedAt()).isNull();
    }

    @Test
    void settle_failedOutcome_marksTheStepAndRunFailedAndStampsFinishedAt() {
        EodPipelineRun claimed =
                run(RunStatus.IN_PROGRESS, StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING);
        when(runs.update(any())).thenAnswer(i -> i.getArgument(0));

        EodPipelineRun settled =
                lifecycle.settle(claimed, PipelineStep.PRICES, StepOutcome.failure("vendor 503"));

        assertThat(settled.stepPricesStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(settled.status()).isEqualTo(RunStatus.FAILED);
        assertThat(settled.finishedAt()).isEqualTo(NOW);
        assertThat(settled.errorMessage()).isEqualTo("vendor 503");
    }
}
