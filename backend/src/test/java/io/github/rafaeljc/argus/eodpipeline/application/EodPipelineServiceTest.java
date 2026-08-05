package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
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
class EodPipelineServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private RunSymbolsStep runSymbolsStep;

    @Mock
    private RunPricesStep runPricesStep;

    @Mock
    private RunEvaluateStep runEvaluateStep;

    @Mock
    private TriggerRun triggerRun;

    private EodPipelineService service;

    @BeforeEach
    void setUp() {
        service = new EodPipelineService(runSymbolsStep, runPricesStep, runEvaluateStep, triggerRun);
    }

    private static EodPipelineRun pendingRun() {
        return new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void runSymbols_delegatesToRunSymbolsStep() {
        EodPipelineRun run = pendingRun();
        when(runSymbolsStep.execute(RUN_ID)).thenReturn(run);

        EodPipelineRun result = service.runSymbols(RUN_ID);

        assertThat(result).isEqualTo(run);
    }

    @Test
    void runPrices_delegatesToRunPricesStep() {
        EodPipelineRun run = pendingRun();
        when(runPricesStep.execute(RUN_ID)).thenReturn(run);

        EodPipelineRun result = service.runPrices(RUN_ID);

        assertThat(result).isEqualTo(run);
    }

    @Test
    void runEvaluate_delegatesToRunEvaluateStep() {
        EodPipelineRun run = pendingRun();
        when(runEvaluateStep.execute(RUN_ID)).thenReturn(run);

        EodPipelineRun result = service.runEvaluate(RUN_ID);

        assertThat(result).isEqualTo(run);
    }

    @Test
    void triggerPipelineRun_delegatesToTriggerRun() {
        EodPipelineRun run = pendingRun();
        when(triggerRun.execute(run.runDate(), Trigger.ADMIN)).thenReturn(run);

        EodPipelineRun result = service.triggerPipelineRun(run.runDate(), Trigger.ADMIN);

        assertThat(result).isEqualTo(run);
    }
}
