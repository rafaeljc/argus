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
}
