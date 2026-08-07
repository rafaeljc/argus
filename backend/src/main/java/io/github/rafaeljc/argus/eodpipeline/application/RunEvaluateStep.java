package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RunEvaluateStep {

    private static final Logger log = LoggerFactory.getLogger(RunEvaluateStep.class);

    private final StepExecution stepExecution;
    private final ActiveUserIds activeUserIds;
    private final WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts;

    public RunEvaluateStep(
            StepExecution stepExecution,
            ActiveUserIds activeUserIds,
            WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts) {
        this.stepExecution = stepExecution;
        this.activeUserIds = activeUserIds;
        this.writeSnapshotAndEvaluateAlerts = writeSnapshotAndEvaluateAlerts;
    }

    // Each user's snapshot+evaluate runs in its own transaction (WriteSnapshotAndEvaluateAlerts is
    // REQUIRES_NEW), so one user's failure never rolls back another's already-committed work; the
    // step is failed or succeeded in aggregate afterwards.
    public EodPipelineRun execute(RunId id) {
        return stepExecution.run(id, PipelineStep.EVALUATE, run -> {
            List<UserId> userIds = activeUserIds.find();
            int failures = 0;
            for (UserId userId : userIds) {
                try {
                    writeSnapshotAndEvaluateAlerts.forUser(userId, run.runDate());
                } catch (RuntimeException e) {
                    failures++;
                    log.warn("eod evaluate failed for user {} run {}", userId.value(), id.value(), e);
                }
            }
            return failures == 0
                    ? StepOutcome.success()
                    : StepOutcome.failure("evaluate failed for %d of %d users".formatted(failures, userIds.size()));
        });
    }
}
