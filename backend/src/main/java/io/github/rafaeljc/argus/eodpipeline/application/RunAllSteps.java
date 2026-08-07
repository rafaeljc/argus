package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import java.util.List;
import java.util.function.Function;
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

    // Not @Transactional: each step claims and settles in its own short transactions and does its
    // work outside one, so a transaction here would swallow all of them and put the vendor calls
    // back under an open transaction. Depends on the three step use cases directly, and not on
    // EodPipelineService, to avoid a bean cycle: ExecutorRunDispatcher (this class's caller) is
    // itself reached from EodPipelineService via TriggerRun, so this class must not point back at
    // the facade.
    public void forRun(RunId id) {
        fromStep(id, PipelineStep.SYMBOLS);
    }

    // Re-enters the pipeline at entryStep and runs through to the end, so a rerun always reaches
    // a terminal status via markSucceeded below — see RerunFromStep for why a rerun can never
    // stop partway without stranding the run at IN_PROGRESS.
    public void fromStep(RunId id, PipelineStep entryStep) {
        EodPipelineRun stepResult = null;
        for (Function<RunId, EodPipelineRun> step : stepsFrom(entryStep)) {
            stepResult = step.apply(id);
            if (stepResult.status() == RunStatus.FAILED) {
                return;
            }
        }
        // stepsFrom always returns at least one step (PipelineStep has 3 values), so stepResult
        // is guaranteed non-null here.
        markSucceeded(stepResult);
    }

    private List<Function<RunId, EodPipelineRun>> stepsFrom(PipelineStep entryStep) {
        List<Function<RunId, EodPipelineRun>> all = List.of(
                runSymbolsStep::execute, runPricesStep::execute, runEvaluateStep::execute);
        return all.subList(entryStep.ordinal(), all.size());
    }

    private void markSucceeded(EodPipelineRun run) {
        runs.update(run.succeeded(clock.now()));
    }
}
