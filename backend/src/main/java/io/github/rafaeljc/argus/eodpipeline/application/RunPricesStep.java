package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.Ticker;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.marketdata.application.SyncDailyCloses;
import io.github.rafaeljc.argus.portfolio.application.port.HeldTickers;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RunPricesStep {

    private final StepExecution stepExecution;
    private final ActiveUserIds activeUserIds;
    private final HeldTickers heldTickers;
    private final SyncDailyCloses syncDailyCloses;

    public RunPricesStep(
            StepExecution stepExecution,
            ActiveUserIds activeUserIds,
            HeldTickers heldTickers,
            SyncDailyCloses syncDailyCloses) {
        this.stepExecution = stepExecution;
        this.activeUserIds = activeUserIds;
        this.heldTickers = heldTickers;
        this.syncDailyCloses = syncDailyCloses;
    }

    // Independently callable: does not require stepSymbolsStatus to have already succeeded — step
    // ordering belongs to the trigger, and the admin re-run endpoint invokes single steps by design.
    public EodPipelineRun execute(RunId id) {
        return stepExecution.run(id, PipelineStep.PRICES, run -> {
            List<UserId> userIds = activeUserIds.find();
            Set<Ticker> tickers = heldTickers.findForUserIds(userIds);
            syncDailyCloses.sync(tickers, run.runDate());
            return StepOutcome.success();
        });
    }
}
