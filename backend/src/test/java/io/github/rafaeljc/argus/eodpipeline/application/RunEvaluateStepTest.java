package io.github.rafaeljc.argus.eodpipeline.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.users.application.port.ActiveUserIds;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunEvaluateStepTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 22);
    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId USER_ID = new UserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final UserId SECOND_USER = new UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));

    @Mock
    private StepLifecycle lifecycle;

    @Mock
    private ActiveUserIds activeUserIds;

    @Mock
    private WriteSnapshotAndEvaluateAlerts writeSnapshotAndEvaluateAlerts;

    private RunEvaluateStep runEvaluateStep;

    @BeforeEach
    void setUp() {
        runEvaluateStep = new RunEvaluateStep(
                new StepExecution(lifecycle), activeUserIds, writeSnapshotAndEvaluateAlerts);
    }

    private static EodPipelineRun claimedRun() {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, null);
    }

    @Test
    void execute_everyUserEvaluates_settlesAsSucceeded() {
        EodPipelineRun claimed = claimedRun();
        when(lifecycle.claim(RUN_ID, PipelineStep.EVALUATE)).thenReturn(claimed);
        when(activeUserIds.find()).thenReturn(List.of(USER_ID));

        runEvaluateStep.execute(RUN_ID);

        verify(writeSnapshotAndEvaluateAlerts).forUser(USER_ID, RUN_DATE);
        verify(lifecycle).settle(claimed, PipelineStep.EVALUATE, StepOutcome.success());
    }

    @Test
    void execute_noActiveUsers_settlesAsSucceededWithoutEvaluatingAnyUser() {
        when(lifecycle.claim(RUN_ID, PipelineStep.EVALUATE)).thenReturn(claimedRun());
        when(activeUserIds.find()).thenReturn(List.of());

        runEvaluateStep.execute(RUN_ID);

        verifyNoInteractions(writeSnapshotAndEvaluateAlerts);
        verify(lifecycle).settle(any(), eq(PipelineStep.EVALUATE), eq(StepOutcome.success()));
    }

    @Test
    void execute_oneUserThrows_continuesRemainingUsersAndSettlesAsFailed() {
        when(lifecycle.claim(RUN_ID, PipelineStep.EVALUATE)).thenReturn(claimedRun());
        when(activeUserIds.find()).thenReturn(List.of(USER_ID, SECOND_USER));
        doThrow(new RuntimeException("db blip"))
                .when(writeSnapshotAndEvaluateAlerts).forUser(USER_ID, RUN_DATE);

        runEvaluateStep.execute(RUN_ID);

        verify(writeSnapshotAndEvaluateAlerts).forUser(SECOND_USER, RUN_DATE);
        verify(lifecycle).settle(
                any(),
                eq(PipelineStep.EVALUATE),
                eq(StepOutcome.failure("evaluate failed for 1 of 2 users")));
    }
}
