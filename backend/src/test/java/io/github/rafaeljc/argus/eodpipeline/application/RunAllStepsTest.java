package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunAllStepsTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:30:00Z");
    private static final Instant LATER = Instant.parse("2026-06-22T22:00:00Z");
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);

    @Mock
    private RunSymbolsStep runSymbolsStep;

    @Mock
    private RunPricesStep runPricesStep;

    @Mock
    private RunEvaluateStep runEvaluateStep;

    @Mock
    private EodPipelineRunRepository runs;

    private RunAllSteps runAllSteps;

    @BeforeEach
    void setUp() {
        runAllSteps = new RunAllSteps(runSymbolsStep, runPricesStep, runEvaluateStep, runs, new FixedClock(LATER));
    }

    private static EodPipelineRun runWith(RunStatus status) {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, status, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void forRun_allStepsSucceed_runsInOrderThenMarksRunSucceeded() {
        when(runSymbolsStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(runPricesStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(runEvaluateStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));

        runAllSteps.forRun(RUN_ID);

        verify(runSymbolsStep).execute(RUN_ID);
        verify(runPricesStep).execute(RUN_ID);
        verify(runEvaluateStep).execute(RUN_ID);
        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs).update(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(captor.getValue().finishedAt()).isEqualTo(LATER);
    }

    @Test
    void forRun_symbolsStepFails_stopsAndSkipsPricesEvaluateAndMarkSucceeded() {
        when(runSymbolsStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.FAILED));

        runAllSteps.forRun(RUN_ID);

        verify(runSymbolsStep).execute(RUN_ID);
        verify(runPricesStep, never()).execute(RUN_ID);
        verify(runEvaluateStep, never()).execute(RUN_ID);
        verify(runs, never()).update(any());
    }

    @Test
    void forRun_pricesStepFails_stopsAndSkipsEvaluateAndMarkSucceeded() {
        when(runSymbolsStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(runPricesStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.FAILED));

        runAllSteps.forRun(RUN_ID);

        verify(runPricesStep).execute(RUN_ID);
        verify(runEvaluateStep, never()).execute(RUN_ID);
        verify(runs, never()).update(any());
    }

    @Test
    void forRun_evaluateStepFails_doesNotMarkSucceeded() {
        when(runSymbolsStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(runPricesStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(runEvaluateStep.execute(RUN_ID)).thenReturn(runWith(RunStatus.FAILED));

        runAllSteps.forRun(RUN_ID);

        verify(runEvaluateStep).execute(RUN_ID);
        verify(runs, never()).update(any());
    }
}
