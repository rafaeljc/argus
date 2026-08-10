package io.github.rafaeljc.argus.eodpipeline.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.event.EodRunTriggered;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TriggerRun {

    private final EodPipelineRunRepository runs;
    private final RunDispatcher dispatcher;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public TriggerRun(EodPipelineRunRepository runs,
                      RunDispatcher dispatcher,
                      Clock clock,
                      ApplicationEventPublisher events) {
        this.runs = runs;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.events = events;
    }

    // actorId is null for the cron trigger (EodPipelineScheduler calls this directly, bypassing
    // EodPipelineService) and non-null when an admin triggered the run through the API. The
    // audit event only fires when there is an actual actor to attribute it to.
    @Transactional
    public EodPipelineRun execute(LocalDate runDate, Trigger trigger, UserId actorId) {
        if (runs.findActiveForDate(runDate).isPresent()) {
            throw new RunAlreadyActiveException(runDate);
        }

        EodPipelineRun run = new EodPipelineRun(
                new RunId(UuidCreator.getTimeOrderedEpoch()),
                runDate,
                trigger,
                RunStatus.IN_PROGRESS,
                clock.now(),
                null,
                StepStatus.PENDING,
                StepStatus.PENDING,
                StepStatus.PENDING,
                null);
        EodPipelineRun inserted = runs.insert(run);
        dispatcher.dispatch(inserted.id());
        if (actorId != null) {
            events.publishEvent(new EodRunTriggered(inserted.id(), runDate, actorId));
        }
        return inserted;
    }
}
