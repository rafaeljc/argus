package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EodPipelineService {

    private final RunSymbolsStep runSymbolsStep;
    private final RunPricesStep runPricesStep;
    private final RunEvaluateStep runEvaluateStep;
    private final TriggerRun triggerRun;
    private final RerunFromStep rerunFromStep;
    private final ListRuns listRuns;
    private final GetRun getRun;

    public EodPipelineService(
            RunSymbolsStep runSymbolsStep,
            RunPricesStep runPricesStep,
            RunEvaluateStep runEvaluateStep,
            TriggerRun triggerRun,
            RerunFromStep rerunFromStep,
            ListRuns listRuns,
            GetRun getRun) {
        this.runSymbolsStep = runSymbolsStep;
        this.runPricesStep = runPricesStep;
        this.runEvaluateStep = runEvaluateStep;
        this.triggerRun = triggerRun;
        this.rerunFromStep = rerunFromStep;
        this.listRuns = listRuns;
        this.getRun = getRun;
    }

    // Must not be @Transactional, and neither must the three step use cases. A step claims, does
    // its vendor work, then settles, in three separate transactions; wrapping that in an outer
    // transaction would put the vendor call back inside one and hide the claim from every other
    // connection until it committed, which is exactly what made a concurrent rerun block.
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

    // Not @Transactional: RerunFromStep.execute is itself @Transactional, matching the
    // triggerPipelineRun comment above.
    public EodPipelineRun rerunFromStep(RunId id, PipelineStep entryStep) {
        return rerunFromStep.execute(id, entryStep);
    }

    @Transactional(readOnly = true)
    public PageResult<EodPipelineRun> listRuns(int page, int perPage) {
        return listRuns.list(page, perPage);
    }

    @Transactional(readOnly = true)
    public EodPipelineRun getRun(RunId id) {
        return getRun.get(id);
    }
}
