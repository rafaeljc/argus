package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.RunId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EodPipelineRunTest {

    private static final RunId RUN_ID = new RunId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 6, 15);
    private static final Instant NOW = Instant.parse("2026-06-15T21:00:00Z");

    @Test
    void constructor_pendingRunWithMinimalFields_isAllowed() {
        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);

        assertThat(run.id()).isEqualTo(RUN_ID);
        assertThat(run.runDate()).isEqualTo(RUN_DATE);
        assertThat(run.trigger()).isEqualTo(Trigger.CRON);
        assertThat(run.status()).isEqualTo(RunStatus.PENDING);
        assertThat(run.startedAt()).isEqualTo(NOW);
        assertThat(run.finishedAt()).isNull();
        assertThat(run.stepSymbolsStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(run.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(run.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(run.errorMessage()).isNull();
    }

    @Test
    void constructor_finishedRunWithAllFields_isAllowed() {
        Instant finishedAt = NOW.plusSeconds(120);

        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.ADMIN, RunStatus.FAILED, NOW, finishedAt,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.FAILED, "vendor 503");

        assertThat(run.finishedAt()).isEqualTo(finishedAt);
        assertThat(run.errorMessage()).isEqualTo("vendor 503");
    }

    @Test
    void constructor_finishedAtEqualsStartedAt_isAllowed() {
        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.SUCCEEDED, NOW, NOW,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null);

        assertThat(run.finishedAt()).isEqualTo(run.startedAt());
    }

    @Test
    void constructor_finishedAtBeforeStartedAt_throwsIllegalArgument() {
        Instant earlier = NOW.minusSeconds(1);

        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.FAILED, NOW, earlier,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                null, RUN_DATE, Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullRunDate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, null, Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullTrigger_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, null, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, null, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStartedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.PENDING, null, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStepSymbolsStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.PENDING, NOW, null,
                null, StepStatus.PENDING, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStepPricesStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, null, StepStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullStepEvaluateStatus_throwsIllegalArgument() {
        assertThatThrownBy(() -> new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stepInProgress_oneStepRunning_namesThatStep() {
        EodPipelineRun run = run(RunStatus.IN_PROGRESS,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING);

        assertThat(run.stepInProgress()).contains(PipelineStep.PRICES);
    }

    @Test
    void stepInProgress_noStepRunning_isEmpty() {
        EodPipelineRun run = run(RunStatus.SUCCEEDED,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED);

        assertThat(run.stepInProgress()).isEmpty();
    }

    @Test
    void startingStep_previouslyFailedRun_clearsFinishedAtAndErrorMessage() {
        EodPipelineRun failed = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.FAILED, NOW, NOW.plusSeconds(60),
                StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.PENDING, "vendor 503");

        EodPipelineRun started = failed.startingStep(PipelineStep.PRICES);

        assertThat(started.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(started.stepPricesStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(started.finishedAt()).isNull();
        assertThat(started.errorMessage()).isNull();
    }

    @Test
    void startingStep_otherSteps_areLeftUntouched() {
        EodPipelineRun run = run(RunStatus.IN_PROGRESS,
                StepStatus.SUCCEEDED, StepStatus.PENDING, StepStatus.SKIPPED);

        EodPipelineRun started = run.startingStep(PipelineStep.PRICES);

        assertThat(started.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(started.stepEvaluateStatus()).isEqualTo(StepStatus.SKIPPED);
    }

    @Test
    void withStepSucceeded_finalStep_leavesRunInProgress() {
        EodPipelineRun run = run(RunStatus.IN_PROGRESS,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS);

        EodPipelineRun settled = run.withStepSucceeded(PipelineStep.EVALUATE);

        assertThat(settled.stepEvaluateStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(settled.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(settled.finishedAt()).isNull();
    }

    @Test
    void withStepFailed_recordsMessageAndFailsTheWholeRun() {
        Instant finishedAt = NOW.plusSeconds(30);
        EodPipelineRun run = run(RunStatus.IN_PROGRESS,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING);

        EodPipelineRun settled = run.withStepFailed(PipelineStep.PRICES, finishedAt, "vendor 503");

        assertThat(settled.stepPricesStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(settled.status()).isEqualTo(RunStatus.FAILED);
        assertThat(settled.finishedAt()).isEqualTo(finishedAt);
        assertThat(settled.errorMessage()).isEqualTo("vendor 503");
    }

    @Test
    void succeeded_allStepsDone_setsTerminalStatusAndKeepsStepStatuses() {
        Instant finishedAt = NOW.plusSeconds(90);
        EodPipelineRun run = run(RunStatus.IN_PROGRESS,
                StepStatus.SUCCEEDED, StepStatus.SKIPPED, StepStatus.SUCCEEDED);

        EodPipelineRun settled = run.succeeded(finishedAt);

        assertThat(settled.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(settled.finishedAt()).isEqualTo(finishedAt);
        assertThat(settled.stepPricesStatus()).isEqualTo(StepStatus.SKIPPED);
    }

    @Test
    void restartingFrom_middleStep_leavesEarlierStepsUntouchedAndResetsLaterOnes() {
        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, RunStatus.FAILED, NOW, NOW.plusSeconds(60),
                StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SUCCEEDED, "vendor 503");

        EodPipelineRun restarted = run.restartingFrom(PipelineStep.PRICES);

        assertThat(restarted.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(restarted.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(restarted.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(restarted.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(restarted.finishedAt()).isNull();
        assertThat(restarted.errorMessage()).isNull();
    }

    @Test
    void restartingFrom_firstStep_resetsEveryStep() {
        EodPipelineRun run = run(RunStatus.SUCCEEDED,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED);

        EodPipelineRun restarted = run.restartingFrom(PipelineStep.SYMBOLS);

        assertThat(restarted.stepSymbolsStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(restarted.stepPricesStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(restarted.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    void restartingFrom_entryStep_leavesItPendingSoTheWorkerCanClaimIt() {
        EodPipelineRun run = run(RunStatus.FAILED,
                StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.PENDING);

        EodPipelineRun restarted = run.restartingFrom(PipelineStep.PRICES);

        assertThat(restarted.stepInProgress()).isEmpty();
    }

    private static EodPipelineRun run(
            RunStatus status, StepStatus symbols, StepStatus prices, StepStatus evaluate) {
        return new EodPipelineRun(
                RUN_ID, RUN_DATE, Trigger.CRON, status, NOW, null, symbols, prices, evaluate, null);
    }
}
