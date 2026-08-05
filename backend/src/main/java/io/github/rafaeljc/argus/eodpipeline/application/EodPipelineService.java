package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class EodPipelineService {

    private final RunSymbolsStep runSymbolsStep;
    private final RunPricesStep runPricesStep;
    private final RunEvaluateStep runEvaluateStep;
    private final TriggerRun triggerRun;

    public EodPipelineService(
            RunSymbolsStep runSymbolsStep,
            RunPricesStep runPricesStep,
            RunEvaluateStep runEvaluateStep,
            TriggerRun triggerRun) {
        this.runSymbolsStep = runSymbolsStep;
        this.runPricesStep = runPricesStep;
        this.runEvaluateStep = runEvaluateStep;
        this.triggerRun = triggerRun;
    }

    // Not @Transactional: each step's execute() already owns its own transaction boundary,
    // because RunAllSteps (this step's other caller) invokes it directly, bypassing this facade.
    // Adding a transaction here too would be redundant ceremony.
    public EodPipelineRun runSymbols(RunId id) {
        return runSymbolsStep.execute(id);
    }

    public EodPipelineRun runPrices(RunId id) {
        return runPricesStep.execute(id);
    }

    public EodPipelineRun runEvaluate(RunId id) {
        return runEvaluateStep.execute(id);
    }

    // Not @Transactional: TriggerRun.execute is itself @Transactional because
    // EodPipelineScheduler (infrastructure) calls it directly, bypassing this facade. Adding a
    // transaction here too would be redundant ceremony on top of the boundary TriggerRun already owns.
    public EodPipelineRun triggerPipelineRun(LocalDate runDate, Trigger trigger) {
        return triggerRun.execute(runDate, trigger);
    }
}
