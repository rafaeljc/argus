package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import org.springframework.stereotype.Service;

@Service
public class RunAllSteps {

    private final RunSymbolsStep runSymbolsStep;
    private final RunPricesStep runPricesStep;
    private final RunEvaluateStep runEvaluateStep;
    private final EodPipelineRunRepository runs;
    private final Clock clock;

    public RunAllSteps(
            RunSymbolsStep runSymbolsStep,
            RunPricesStep runPricesStep,
            RunEvaluateStep runEvaluateStep,
            EodPipelineRunRepository runs,
            Clock clock) {
        this.runSymbolsStep = runSymbolsStep;
        this.runPricesStep = runPricesStep;
        this.runEvaluateStep = runEvaluateStep;
        this.runs = runs;
        this.clock = clock;
    }

    // Not @Transactional: each step's execute() demarcates its own transaction, as does
    // markSucceeded below — self-invoking across those calls from a single @Transactional method
    // here would bypass the Spring proxy and collapse them into one transaction. Depends on the
    // three step use cases directly, and not on EodPipelineService, to avoid a bean cycle:
    // ExecutorRunDispatcher (this class's caller) is itself reached from EodPipelineService via
    // TriggerRun, so this class must not point back at the facade.
    public void forRun(RunId id) {
        EodPipelineRun afterSymbols = runSymbolsStep.execute(id);
        if (afterSymbols.status() == RunStatus.FAILED) {
            return;
        }

        EodPipelineRun afterPrices = runPricesStep.execute(id);
        if (afterPrices.status() == RunStatus.FAILED) {
            return;
        }

        EodPipelineRun afterEvaluate = runEvaluateStep.execute(id);
        if (afterEvaluate.status() == RunStatus.FAILED) {
            return;
        }

        markSucceeded(afterEvaluate);
    }

    private void markSucceeded(EodPipelineRun run) {
        EodPipelineRun succeeded = new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), RunStatus.SUCCEEDED, run.startedAt(), clock.now(),
                run.stepSymbolsStatus(), run.stepPricesStatus(), run.stepEvaluateStatus(), run.errorMessage());
        runs.update(succeeded);
    }
}
