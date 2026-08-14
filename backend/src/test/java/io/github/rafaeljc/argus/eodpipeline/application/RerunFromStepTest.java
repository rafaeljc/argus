package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.event.EodStepRerunTriggered;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.PriorStepNotSucceededException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunNotSettledException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RerunFromStepTest {

    private static final Instant STARTED_AT = Instant.parse("2026-06-22T21:30:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-06-22T21:45:00Z");
    private static final RunId RUN_ID = new RunId(UUID.randomUUID());
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);
    private static final UserId ACTOR_ID = new UserId(UUID.randomUUID());

    @Mock
    private EodPipelineRunRepository runs;

    @Mock
    private RunDispatcher dispatcher;

    @Mock
    private ApplicationEventPublisher events;

    private RerunFromStep rerunFromStep;

    @BeforeEach
    void setUp() {
        rerunFromStep = new RerunFromStep(runs, dispatcher, events);
    }

    private static EodPipelineRun failedRun(StepStatus symbols, StepStatus prices, StepStatus evaluate) {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.FAILED, STARTED_AT, FINISHED_AT,
                symbols, prices, evaluate, "boom");
    }

    private void guardAccepts() {
        when(runs.updateIfRunTerminal(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
    }

    @Test
    void execute_unknownRun_throwsResourceNotFound() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.PRICES, ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(runs, never()).updateIfRunTerminal(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void execute_runNotSettled_throwsRunNotSettledAndNeverDispatches() {
        EodPipelineRun active = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, STARTED_AT, null,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(active));
        when(runs.updateIfRunTerminal(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.PRICES, ACTOR_ID))
                .isInstanceOf(RunNotSettledException.class)
                .extracting("runId", "runStatus")
                .containsExactly(RUN_ID, RunStatus.IN_PROGRESS);

        verifyNoInteractions(dispatcher);
    }

    @Test
    void execute_lostTheClaimRace_reportsTheStatusThatBlockedIt() {
        EodPipelineRun settled = failedRun(StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED);
        EodPipelineRun reclaimedByAnother = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, STARTED_AT, null,
                StepStatus.SUCCEEDED, StepStatus.PENDING, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(settled))
                .thenReturn(Optional.of(reclaimedByAnother));
        when(runs.updateIfRunTerminal(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.PRICES, ACTOR_ID))
                .isInstanceOf(RunNotSettledException.class)
                .extracting("runStatus")
                .isEqualTo(RunStatus.IN_PROGRESS);
    }

    @Test
    void execute_fromPrices_resetsPricesAndEvaluateLeavesSymbolsAndDispatchesFromPrices() {
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(failedRun(StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED)));
        guardAccepts();

        EodPipelineRun result = rerunFromStep.execute(RUN_ID, PipelineStep.PRICES, ACTOR_ID);

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.finishedAt()).isNull();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);

        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs).updateIfRunTerminal(captor.capture());
        assertThat(captor.getValue()).isEqualTo(result);
        verify(dispatcher).dispatchFrom(RUN_ID, PipelineStep.PRICES);
    }

    @Test
    void execute_fromEvaluateWithPricesFailed_throwsAndNeverClaimsOrDispatches() {
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(failedRun(StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.PENDING)));

        assertThatThrownBy(() -> rerunFromStep.execute(RUN_ID, PipelineStep.EVALUATE, ACTOR_ID))
                .isInstanceOf(PriorStepNotSucceededException.class)
                .extracting("blockingStep", "blockingStatus")
                .containsExactly(PipelineStep.PRICES, StepStatus.FAILED);

        verify(runs, never()).updateIfRunTerminal(any());
        verifyNoInteractions(dispatcher, events);
    }

    @Test
    void execute_fromEvaluate_resetsOnlyEvaluate() {
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(failedRun(StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.FAILED)));
        guardAccepts();

        EodPipelineRun result = rerunFromStep.execute(RUN_ID, PipelineStep.EVALUATE, ACTOR_ID);

        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        verify(dispatcher).dispatchFrom(RUN_ID, PipelineStep.EVALUATE);
    }

    @Test
    void execute_fromSymbols_resetsAllThreeSteps() {
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(failedRun(StepStatus.FAILED, StepStatus.SKIPPED, StepStatus.SKIPPED)));
        guardAccepts();

        EodPipelineRun result = rerunFromStep.execute(RUN_ID, PipelineStep.SYMBOLS, ACTOR_ID);

        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        verify(dispatcher).dispatchFrom(RUN_ID, PipelineStep.SYMBOLS);
    }

    @Test
    void execute_stateChanged_publishesEodStepRerunTriggeredEvent() {
        when(runs.findById(RUN_ID))
                .thenReturn(Optional.of(failedRun(StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED)));
        guardAccepts();

        rerunFromStep.execute(RUN_ID, PipelineStep.PRICES, ACTOR_ID);

        ArgumentCaptor<EodStepRerunTriggered> captor = ArgumentCaptor.forClass(EodStepRerunTriggered.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().runId()).isEqualTo(RUN_ID);
        assertThat(captor.getValue().step()).isEqualTo(PipelineStep.PRICES);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR_ID);
    }
}
