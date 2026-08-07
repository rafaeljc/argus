package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.github.rafaeljc.argus.marketdata.application.SyncSymbolUniverse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Runs the real StepExecution over a mocked StepLifecycle: the claim/settle transactions belong to
// StepLifecycleTest, so what is under test here is the work body and the outcome it reports.
@ExtendWith(MockitoExtension.class)
class RunSymbolsStepTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private StepLifecycle lifecycle;

    @Mock
    private SyncSymbolUniverse syncSymbolUniverse;

    private RunSymbolsStep runSymbolsStep;

    @BeforeEach
    void setUp() {
        runSymbolsStep = new RunSymbolsStep(new StepExecution(lifecycle), syncSymbolUniverse);
    }

    private static EodPipelineRun claimedRun() {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void execute_syncSucceeds_settlesTheClaimedRunAsSucceeded() {
        EodPipelineRun claimed = claimedRun();
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimed);

        runSymbolsStep.execute(RUN_ID);

        verify(syncSymbolUniverse).sync();
        verify(lifecycle).settle(claimed, PipelineStep.SYMBOLS, StepOutcome.success());
    }

    @Test
    void execute_syncThrows_settlesAsFailedCarryingTheVendorMessage() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS)).thenReturn(claimedRun());
        when(syncSymbolUniverse.sync()).thenThrow(new RuntimeException("vendor 503"));

        runSymbolsStep.execute(RUN_ID);

        verify(lifecycle).settle(any(), eq(PipelineStep.SYMBOLS), eq(StepOutcome.failure("vendor 503")));
    }

    @Test
    void execute_claimRejected_neverSyncsAndNeverSettles() {
        when(lifecycle.claim(RUN_ID, PipelineStep.SYMBOLS))
                .thenThrow(new StepInProgressException(RUN_ID, PipelineStep.PRICES));

        assertThatThrownBy(() -> runSymbolsStep.execute(RUN_ID)).isInstanceOf(StepInProgressException.class);

        verify(syncSymbolUniverse, never()).sync();
        verify(lifecycle, never()).settle(any(), any(), any());
    }
}
