package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggerRunTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:30:00Z");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);

    @Mock
    private EodPipelineRunRepository runs;

    @Mock
    private RunDispatcher dispatcher;

    private TriggerRun triggerRun;

    @BeforeEach
    void setUp() {
        triggerRun = new TriggerRun(runs, dispatcher, new FixedClock(NOW));
    }

    @Test
    void execute_noActiveRunForDate_insertsInProgressRunWithPendingStepsAndDispatches() {
        when(runs.findActiveForDate(RUN_DATE)).thenReturn(Optional.empty());
        when(runs.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EodPipelineRun result = triggerRun.execute(RUN_DATE, Trigger.ADMIN);

        assertThat(result.runDate()).isEqualTo(RUN_DATE);
        assertThat(result.trigger()).isEqualTo(Trigger.ADMIN);
        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isEqualTo(NOW);
        assertThat(result.finishedAt()).isNull();
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.errorMessage()).isNull();

        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs).insert(captor.capture());
        assertThat(captor.getValue()).isEqualTo(result);
        verify(dispatcher).dispatch(result.id());
    }

    @Test
    void execute_activeRunAlreadyExistsForDate_throwsAndNeverInsertsOrDispatches() {
        EodPipelineRun active = new EodPipelineRun(
                new RunId(UUID.randomUUID()),
                RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, null);
        when(runs.findActiveForDate(RUN_DATE)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> triggerRun.execute(RUN_DATE, Trigger.ADMIN))
                .isInstanceOf(RunAlreadyActiveException.class)
                .extracting("runDate")
                .isEqualTo(RUN_DATE);

        verify(runs, never()).insert(any());
        verifyNoInteractions(dispatcher);
    }
}
