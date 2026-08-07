package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepInProgressException;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepExecutionTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private StepLifecycle lifecycle;

    private StepExecution stepExecution;

    @BeforeEach
    void setUp() {
        stepExecution = new StepExecution(lifecycle);
    }

    private static EodPipelineRun claimedRun() {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void run_work_happensBetweenTheClaimAndTheSettle() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimedRun());
        List<String> order = new ArrayList<>();

        stepExecution.run(RUN_ID, PipelineStep.SYMBOLS, run -> {
            order.add("work");
            return StepOutcome.success();
        });

        assertThat(order).containsExactly("work");
        InOrder inOrder = inOrder(lifecycle);
        inOrder.verify(lifecycle).claim(RUN_ID, PipelineStep.SYMBOLS);
        inOrder.verify(lifecycle).settle(any(), any(), any());
    }

    @Test
    void run_work_receivesTheClaimedRunSoItCanReadTheRunDate() {
        EodPipelineRun claimed = claimedRun();
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimed);

        stepExecution.run(RUN_ID, PipelineStep.SYMBOLS, run -> {
            assertThat(run).isSameAs(claimed);
            return StepOutcome.success();
        });

        verify(lifecycle).settle(claimed, PipelineStep.SYMBOLS, StepOutcome.success());
    }

    @Test
    void run_workThrows_settlesAsFailedRatherThanPropagating() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimedRun());

        stepExecution.run(RUN_ID, PipelineStep.SYMBOLS, run -> {
            throw new RuntimeException("vendor 503");
        });

        verify(lifecycle).settle(any(), any(), eq(StepOutcome.failure("vendor 503")));
    }

    @Test
    void run_workThrowsWithNoMessage_usesTheExceptionClassNameAsTheErrorMessage() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimedRun());

        stepExecution.run(RUN_ID, PipelineStep.SYMBOLS, run -> {
            throw new IllegalStateException();
        });

        verify(lifecycle).settle(any(), any(), eq(StepOutcome.failure("IllegalStateException")));
    }

    @Test
    void run_workThrowsWithBlankMessage_usesTheExceptionClassNameAsTheErrorMessage() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimedRun());

        stepExecution.run(RUN_ID, PipelineStep.SYMBOLS, run -> {
            throw new IllegalStateException("   ");
        });

        verify(lifecycle).settle(any(), any(), eq(StepOutcome.failure("IllegalStateException")));
    }

    @Test
    void run_claimRejected_neverRunsTheWorkAndNeverSettles() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS))
                .thenThrow(new StepInProgressException(RUN_ID, PipelineStep.PRICES));
        List<String> order = new ArrayList<>();

        assertThatThrownBy(() -> stepExecution.run(RUN_ID, PipelineStep.SYMBOLS, run -> {
            order.add("work");
            return StepOutcome.success();
        })).isInstanceOf(StepInProgressException.class);

        assertThat(order).isEmpty();
        verify(lifecycle, never()).settle(any(), any(), any());
    }
}
