package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class StepExecution {

    private final StepLifecycle lifecycle;

    public StepExecution(StepLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    // Deliberately not @Transactional: work runs between the claim and the settle, never inside
    // either. A vendor call under an open transaction would pin a connection for its whole
    // duration and keep the claim uncommitted, so no other request could see that the step is
    // running — which is what makes rejecting a concurrent rerun possible at all.
    public EodPipelineRun run(RunId id, PipelineStep step, Function<EodPipelineRun, StepOutcome> work) {
        EodPipelineRun claimed = lifecycle.claim(id, step);
        return lifecycle.settle(claimed, step, outcomeOf(claimed, work));
    }

    // Never lets a vendor failure escape: the persisted run is the source of truth for a step's
    // outcome, surfaced later through the admin re-run endpoint.
    private static StepOutcome outcomeOf(EodPipelineRun claimed, Function<EodPipelineRun, StepOutcome> work) {
        try {
            return work.apply(claimed);
        } catch (RuntimeException e) {
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return StepOutcome.failure(message);
        }
    }
}
