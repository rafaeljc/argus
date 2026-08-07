package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.marketdata.application.SyncSymbolUniverse;
import org.springframework.stereotype.Service;

@Service
public class RunSymbolsStep {

    private final StepExecution stepExecution;
    private final SyncSymbolUniverse syncSymbolUniverse;

    public RunSymbolsStep(StepExecution stepExecution, SyncSymbolUniverse syncSymbolUniverse) {
        this.stepExecution = stepExecution;
        this.syncSymbolUniverse = syncSymbolUniverse;
    }

    public EodPipelineRun execute(RunId id) {
        return stepExecution.run(id, PipelineStep.SYMBOLS, run -> {
            syncSymbolUniverse.sync();
            return StepOutcome.success();
        });
    }
}
