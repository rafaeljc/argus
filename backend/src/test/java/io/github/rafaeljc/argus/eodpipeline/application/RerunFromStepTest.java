package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RerunFromStepTest {

    private static final Instant STARTED_AT = Instant.parse("2026-06-22T21:30:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-06-22T21:45:00Z");
    private static final RunId RUN_ID = new RunId(UUID.randomUUID());
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);

    @Mock
    private EodPipelineRunRepository runs;

    @Mock
    private RunDispatcher dispatcher;

    @Mock
    private TransactionalMutationLock lock;

    private RerunFromStep rerunFromStep;

    @BeforeEach
    void setUp() {
        rerunFromStep = new RerunFromStep(runs, dispatcher, lock);
    }

    private static EodPipelineRun failedRun(StepStatus symbols, StepStatus prices, StepStatus evaluate) {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.FAILED, STARTED_AT, FINISHED_AT,
                symbols, prices, evaluate, "boom");
    }

    @Test
    void execute_unknownRun_throwsResourceNotFound() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.PRICES))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(runs, never()).update(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void execute_namedStepAlreadyInProgress_throwsStepInProgressAndNeverUpdatesOrDispatches() {
        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, STARTED_AT, null,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.PRICES))
                .isInstanceOf(StepInProgressException.class)
                .extracting("runId", "step")
                .containsExactly(RUN_ID, PipelineStep.PRICES);

        verify(runs, never()).update(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void execute_differentStepInProgress_throwsStepInProgressNamingThatStep() {
        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, STARTED_AT, null,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.EVALUATE))
                .isInstanceOf(StepInProgressException.class)
                .extracting("runId", "step")
                .containsExactly(RUN_ID, PipelineStep.PRICES);

        verify(runs, never()).update(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void execute_validRerun_acquiresLockForRunIdBeforeReadingRun() {
        EodPipelineRun run = failedRun(StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(runs.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        rerunFromStep.execute(RUN_ID, PipelineStep.PRICES);

        InOrder order = inOrder(lock, runs);
        order.verify(lock).acquireResourceById("eod-pipeline-run", RUN_ID.value());
        order.verify(runs).findById(RUN_ID);
    }

    @Test
    void execute_fromPrices_resetsPricesAndEvaluateLeavesSymbolsAndDispatchesFromPrices() {
        EodPipelineRun run = failedRun(StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(runs.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EodPipelineRun result = rerunFromStep.execute(RUN_ID, PipelineStep.PRICES);

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.finishedAt()).isNull();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);

        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs).update(captor.capture());
        assertThat(captor.getValue()).isEqualTo(result);
        verify(dispatcher).dispatchFrom(RUN_ID, PipelineStep.PRICES);
    }

    @Test
    void execute_fromEvaluate_resetsOnlyEvaluate() {
        EodPipelineRun run = failedRun(StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.FAILED);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(runs.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EodPipelineRun result = rerunFromStep.execute(RUN_ID, PipelineStep.EVALUATE);

        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        verify(dispatcher).dispatchFrom(RUN_ID, PipelineStep.EVALUATE);
    }

    @Test
    void execute_fromSymbols_resetsAllThreeSteps() {
        EodPipelineRun run = failedRun(StepStatus.FAILED, StepStatus.SKIPPED, StepStatus.SKIPPED);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(runs.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EodPipelineRun result = rerunFromStep.execute(RUN_ID, PipelineStep.SYMBOLS);

        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        verify(dispatcher).dispatchFrom(RUN_ID, PipelineStep.SYMBOLS);
    }
}
