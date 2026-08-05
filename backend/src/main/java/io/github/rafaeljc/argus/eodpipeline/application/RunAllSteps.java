package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import org.springframework.stereotype.Service;

@Service
public class RunAllSteps {

    private final EodPipelineService service;

    public RunAllSteps(EodPipelineService service) {
        this.service = service;
    }

    // Not @Transactional: each delegated call (runSymbols/runPrices/runEvaluate/markSucceeded)
    // demarcates its own transaction on EodPipelineService. Sequencing lives here instead of on
    // EodPipelineService itself because self-invoking across those methods would bypass the
    // Spring proxy and collapse them into a single transaction.
    public void forRun(RunId id) {
        EodPipelineRun afterSymbols = service.runSymbols(id);
        if (afterSymbols.status() == RunStatus.FAILED) {
            return;
        }

        EodPipelineRun afterPrices = service.runPrices(id);
        if (afterPrices.status() == RunStatus.FAILED) {
            return;
        }

        EodPipelineRun afterEvaluate = service.runEvaluate(id);
        if (afterEvaluate.status() == RunStatus.FAILED) {
            return;
        }

        service.markSucceeded(id);
    }
}
