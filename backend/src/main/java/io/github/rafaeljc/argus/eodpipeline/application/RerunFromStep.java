package io.github.rafaeljc.argus.eodpipeline.application;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.event.EodStepRerunTriggered;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunNotSettledException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RerunFromStep {

    private final EodPipelineRunRepository runs;
    private final RunDispatcher dispatcher;
    private final ApplicationEventPublisher events;

    public RerunFromStep(EodPipelineRunRepository runs, RunDispatcher dispatcher, ApplicationEventPublisher events) {
        this.runs = runs;
        this.dispatcher = dispatcher;
        this.events = events;
    }

    // A rerun may only take a run that has settled. Guarding on the run rather than on its steps
    // also covers the gaps between steps: RunAllSteps advances through three separate transactions,
    // so a run can be mid-sequence with no step in progress at that instant.
    @Transactional
    public EodPipelineRun execute(RunId id, PipelineStep entryStep, UserId actorId) {
        EodPipelineRun run = runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("eod pipeline run not found: " + id.value()));

        EodPipelineRun updated = runs.updateIfRunTerminal(run.restartingFrom(entryStep))
                .orElseThrow(() -> new RunNotSettledException(id, currentStatusOf(id, run)));

        dispatcher.dispatchFrom(id, entryStep);
        events.publishEvent(new EodStepRerunTriggered(id, entryStep, actorId));
        return updated;
    }

    // Re-read on the rejected path only, so the reported status is the one that actually blocked
    // the rerun rather than the one read before losing the race.
    private RunStatus currentStatusOf(RunId id, EodPipelineRun read) {
        return runs.findById(id).orElse(read).status();
    }
}
