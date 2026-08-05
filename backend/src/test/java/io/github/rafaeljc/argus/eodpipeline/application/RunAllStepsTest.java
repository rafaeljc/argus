package io.github.rafaeljc.argus.eodpipeline.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.RunId;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunAllStepsTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:30:00Z");
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);

    @Mock
    private EodPipelineService service;

    private RunAllSteps runAllSteps;

    @BeforeEach
    void setUp() {
        runAllSteps = new RunAllSteps(service);
    }

    private static EodPipelineRun runWith(RunStatus status) {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, status, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void forRun_allStepsSucceed_runsInOrderThenMarksSucceeded() {
        when(service.runSymbols(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(service.runPrices(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(service.runEvaluate(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));

        runAllSteps.forRun(RUN_ID);

        verify(service).runSymbols(RUN_ID);
        verify(service).runPrices(RUN_ID);
        verify(service).runEvaluate(RUN_ID);
        verify(service).markSucceeded(RUN_ID);
    }

    @Test
    void forRun_symbolsStepFails_stopsAndSkipsPricesEvaluateAndMarkSucceeded() {
        when(service.runSymbols(RUN_ID)).thenReturn(runWith(RunStatus.FAILED));

        runAllSteps.forRun(RUN_ID);

        verify(service).runSymbols(RUN_ID);
        verify(service, never()).runPrices(RUN_ID);
        verify(service, never()).runEvaluate(RUN_ID);
        verify(service, never()).markSucceeded(RUN_ID);
    }

    @Test
    void forRun_pricesStepFails_stopsAndSkipsEvaluateAndMarkSucceeded() {
        when(service.runSymbols(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(service.runPrices(RUN_ID)).thenReturn(runWith(RunStatus.FAILED));

        runAllSteps.forRun(RUN_ID);

        verify(service).runPrices(RUN_ID);
        verify(service, never()).runEvaluate(RUN_ID);
        verify(service, never()).markSucceeded(RUN_ID);
    }

    @Test
    void forRun_evaluateStepFails_doesNotMarkSucceeded() {
        when(service.runSymbols(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(service.runPrices(RUN_ID)).thenReturn(runWith(RunStatus.IN_PROGRESS));
        when(service.runEvaluate(RUN_ID)).thenReturn(runWith(RunStatus.FAILED));

        runAllSteps.forRun(RUN_ID);

        verify(service).runEvaluate(RUN_ID);
        verify(service, never()).markSucceeded(RUN_ID);
    }
}
