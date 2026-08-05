package io.github.rafaeljc.argus.eodpipeline.application;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TriggerRun {

    private final EodPipelineRunRepository runs;
    private final RunDispatcher dispatcher;
    private final Clock clock;

    public TriggerRun(EodPipelineRunRepository runs, RunDispatcher dispatcher, Clock clock) {
        this.runs = runs;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Transactional
    public EodPipelineRun execute(LocalDate runDate, Trigger trigger) {
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
        return inserted;
    }
}
