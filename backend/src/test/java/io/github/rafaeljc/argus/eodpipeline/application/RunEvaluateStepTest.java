package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.application.TransactionalMutationLock;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunEvaluateStepTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId USER_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Mock
    private EodPipelineRunRepository runs;

    @Mock
    private ActiveUserIds activeUserIds;

    @Mock
    private WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts;

    @Mock
    private TransactionalMutationLock lock;

    private RunEvaluateStep runEvaluateStep;

    @BeforeEach
    void setUp() {
        runEvaluateStep = new RunEvaluateStep(
                runs, activeUserIds, writeSnapshotAndEvaluateAlerts, lock, new FixedClock(NOW));
    }

    private static EodPipelineRun pendingRun() {
        return new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    @Test
    void execute_pendingRun_acquiresLockForRunIdBeforeReadingRun() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of());

        runEvaluateStep.execute(RUN_ID);

        InOrder order = inOrder(lock, runs);
        order.verify(lock).acquireResourceById("eod-pipeline-run", RUN_ID.value());
        order.verify(runs).findById(RUN_ID);
    }

    @Test
    void execute_pendingRun_startsInProgressThenSucceedsAndMarksStepSucceeded() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));

        EodPipelineRun result = runEvaluateStep.execute(RUN_ID);

        assertThat(result.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(result.stepSymbolsStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(result.finishedAt()).isNull();
        assertThat(result.errorMessage()).isNull();
        verify(writeSnapshotAndEvaluateAlerts).forUser(USER_ID, LocalDate.of(2026, 6, 22));
        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs, times(2)).update(captor.capture());
        EodPipelineRun startedUpdate = captor.getAllValues().get(0);
        assertThat(startedUpdate.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(startedUpdate.stepEvaluateStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(startedUpdate.finishedAt()).isNull();
    }

    @Test
    void execute_noActiveUsers_stillSucceedsWithoutEvaluatingAnyUser() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of());

        EodPipelineRun result = runEvaluateStep.execute(RUN_ID);

        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.SUCCEEDED);
        verifyNoInteractions(writeSnapshotAndEvaluateAlerts);
    }

    @Test
    void execute_oneUserThrows_continuesRemainingUsersAndMarksStepAndRunFailed() {
        UserId secondUser = new UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(pendingRun()));
        when(activeUserIds.find()).thenReturn(List.of(USER_ID, secondUser));
        doThrow(new RuntimeException("db blip"))
                .when(writeSnapshotAndEvaluateAlerts).forUser(USER_ID, LocalDate.of(2026, 6, 22));

        EodPipelineRun result = runEvaluateStep.execute(RUN_ID);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.stepEvaluateStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.errorMessage()).isEqualTo("evaluate failed for 1 of 2 users");
        verify(writeSnapshotAndEvaluateAlerts).forUser(secondUser, LocalDate.of(2026, 6, 22));
    }

    @Test
    void execute_missingRun_throwsResourceNotFoundException() {
        when(runs.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runEvaluateStep.execute(RUN_ID)).isInstanceOf(ResourceNotFoundException.class);

        verify(runs, never()).update(any());
    }

    @Test
    void execute_alreadyInProgressRun_doesNotChangeRunStatusOnStart() {
        EodPipelineRun inProgress = new EodPipelineRun(
                RUN_ID, LocalDate.of(2026, 6, 22), Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(inProgress));
        when(activeUserIds.find()).thenReturn(List.of());

        runEvaluateStep.execute(RUN_ID);

        ArgumentCaptor<EodPipelineRun> captor = ArgumentCaptor.forClass(EodPipelineRun.class);
        verify(runs, times(2)).update(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(RunStatus.IN_PROGRESS);
    }
}
