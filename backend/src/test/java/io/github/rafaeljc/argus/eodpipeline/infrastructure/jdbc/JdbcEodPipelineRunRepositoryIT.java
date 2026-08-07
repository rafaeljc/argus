package io.github.rafaeljc.argus.eodpipeline.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunAlreadyActiveException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(PostgresContainer.class)
@SpringBootTest
class JdbcEodPipelineRunRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-06-15T21:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private EodPipelineRunRepository runs;

    @Test
    void insert_thenFindById_roundTripsAllFields() {
        EodPipelineRun saved = runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 1)));

        EodPipelineRun found = runs.findById(saved.id()).orElseThrow();

        assertThat(found).isEqualTo(saved);
    }

    @Test
    void insert_finishedRunWithErrorMessage_persistsAllOptionalFields() {
        RunId id = newRunId();
        Instant finishedAt = NOW.plusSeconds(120);

        EodPipelineRun saved = runs.insert(new EodPipelineRun(
                id, LocalDate.of(2026, 6, 2), Trigger.ADMIN, RunStatus.FAILED, NOW, finishedAt,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.FAILED, "vendor 503"));

        EodPipelineRun found = runs.findById(id).orElseThrow();
        assertThat(found.status()).isEqualTo(RunStatus.FAILED);
        assertThat(found.finishedAt()).isEqualTo(finishedAt);
        assertThat(found.stepEvaluateStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(found.errorMessage()).isEqualTo("vendor 503");
    }

    @Test
    void findById_missing_returnsEmpty() {
        assertThat(runs.findById(newRunId())).isEmpty();
    }

    @Test
    void insert_secondActiveRunForSameDate_throwsRunAlreadyActiveException() {
        LocalDate runDate = LocalDate.of(2026, 6, 3);
        runs.insert(pendingRun(newRunId(), runDate));

        EodPipelineRun duplicate = pendingRun(newRunId(), runDate);

        assertThatThrownBy(() -> runs.insert(duplicate))
                .isInstanceOf(RunAlreadyActiveException.class)
                .extracting("runDate")
                .isEqualTo(runDate);
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"SUCCEEDED", "FAILED"})
    void insert_secondRunForSameDateAfterFirstRunReachedTerminalStatus_isAllowed(RunStatus terminalStatus) {
        LocalDate runDate = LocalDate.of(2026, 6, 4);
        RunId firstId = newRunId();
        runs.insert(pendingRun(firstId, runDate));
        runs.update(withTerminalStatus(runs.findById(firstId).orElseThrow(), terminalStatus, NOW.plusSeconds(60)));

        EodPipelineRun secondRun = runs.insert(pendingRun(newRunId(), runDate));

        assertThat(runs.findById(secondRun.id())).isPresent();
    }

    @Test
    void update_changesStatusStepStatusFinishedAtAndErrorMessage_persists() {
        RunId id = newRunId();
        EodPipelineRun saved = runs.insert(pendingRun(id, LocalDate.of(2026, 6, 5)));
        Instant finishedAt = NOW.plusSeconds(90);

        EodPipelineRun updated = new EodPipelineRun(
                saved.id(), saved.runDate(), saved.trigger(), RunStatus.FAILED, saved.startedAt(), finishedAt,
                StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED, "prices vendor timeout");

        runs.update(updated);

        EodPipelineRun found = runs.findById(id).orElseThrow();
        assertThat(found.status()).isEqualTo(RunStatus.FAILED);
        assertThat(found.finishedAt()).isEqualTo(finishedAt);
        assertThat(found.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(found.stepPricesStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(found.stepEvaluateStatus()).isEqualTo(StepStatus.SKIPPED);
        assertThat(found.errorMessage()).isEqualTo("prices vendor timeout");
    }

    @Test
    void update_flippingToInProgressCollidesWithAnotherActiveRunForSameDate_throwsRunAlreadyActiveException() {
        LocalDate runDate = LocalDate.of(2026, 6, 16);
        RunId firstId = newRunId();
        EodPipelineRun first = runs.insert(pendingRun(firstId, runDate));
        runs.update(withTerminalStatus(first, RunStatus.FAILED, NOW.plusSeconds(60)));
        RunId secondId = newRunId();
        runs.insert(pendingRun(secondId, runDate));

        EodPipelineRun reactivatedFirst = new EodPipelineRun(
                firstId, runDate, Trigger.CRON, RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, null);

        assertThatThrownBy(() -> runs.update(reactivatedFirst))
                .isInstanceOf(RunAlreadyActiveException.class)
                .extracting("runDate")
                .isEqualTo(runDate);
    }

    @Test
    void findActiveForDate_pendingRun_returnsIt() {
        LocalDate runDate = LocalDate.of(2026, 6, 6);
        EodPipelineRun saved = runs.insert(pendingRun(newRunId(), runDate));

        assertThat(runs.findActiveForDate(runDate)).contains(saved);
    }

    @Test
    void findActiveForDate_inProgressRun_returnsIt() {
        LocalDate runDate = LocalDate.of(2026, 6, 7);
        RunId id = newRunId();
        EodPipelineRun saved = runs.insert(pendingRun(id, runDate));
        EodPipelineRun inProgress = new EodPipelineRun(
                saved.id(), saved.runDate(), saved.trigger(), RunStatus.IN_PROGRESS, saved.startedAt(), null,
                StepStatus.IN_PROGRESS, StepStatus.PENDING, StepStatus.PENDING, null);
        runs.update(inProgress);

        assertThat(runs.findActiveForDate(runDate)).contains(inProgress);
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"SUCCEEDED", "FAILED"})
    void findActiveForDate_onlyTerminalRun_returnsEmpty(RunStatus terminalStatus) {
        LocalDate runDate = LocalDate.of(2026, 6, 8);
        RunId id = newRunId();
        runs.insert(pendingRun(id, runDate));
        runs.update(withTerminalStatus(runs.findById(id).orElseThrow(), terminalStatus, NOW.plusSeconds(60)));

        assertThat(runs.findActiveForDate(runDate)).isEmpty();
    }

    @Test
    void findActiveForDate_noRuns_returnsEmpty() {
        assertThat(runs.findActiveForDate(LocalDate.of(2026, 6, 9))).isEmpty();
    }

    @Test
    void listPaged_returnsRunsOrderedByStartedAtDescending() {
        EodPipelineRun oldest = runs.insert(new EodPipelineRun(
                newRunId(), LocalDate.of(2026, 6, 10), Trigger.CRON, RunStatus.SUCCEEDED, NOW, NOW.plusSeconds(60),
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null));
        EodPipelineRun newest = runs.insert(new EodPipelineRun(
                newRunId(), LocalDate.of(2026, 6, 11), Trigger.CRON, RunStatus.SUCCEEDED,
                NOW.plusSeconds(3600), NOW.plusSeconds(3660),
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null));

        List<EodPipelineRun> page = runs.listPaged(1, 10);

        assertThat(page).extracting(EodPipelineRun::id).containsExactly(newest.id(), oldest.id());
    }

    @Test
    void listPaged_respectsPageAndPerPage() {
        for (int i = 0; i < 3; i++) {
            runs.insert(new EodPipelineRun(
                    newRunId(), LocalDate.of(2026, 6, 20).plusDays(i), Trigger.CRON, RunStatus.SUCCEEDED,
                    NOW.plusSeconds(i * 3600L), NOW.plusSeconds(i * 3600L + 60),
                    StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null));
        }

        List<EodPipelineRun> firstPage = runs.listPaged(1, 2);
        List<EodPipelineRun> secondPage = runs.listPaged(2, 2);

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
    }

    @Test
    void count_noRuns_returnsZero() {
        assertThat(runs.count()).isZero();
    }

    @Test
    void count_multipleRuns_returnsTotalRowCount() {
        runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 12)));
        runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 13)));
        runs.insert(pendingRun(newRunId(), LocalDate.of(2026, 6, 14)));

        assertThat(runs.count()).isEqualTo(3);
    }

    @Test
    void updateIfNoStepInProgress_noStepRunning_appliesTheUpdate() {
        RunId id = newRunId();
        EodPipelineRun saved = runs.insert(pendingRun(id, LocalDate.of(2026, 6, 17)));

        EodPipelineRun claimed = saved.startingStep(PipelineStep.PRICES);

        assertThat(runs.updateIfNoStepInProgress(claimed)).contains(claimed);
        assertThat(runs.findById(id).orElseThrow().stepPricesStatus()).isEqualTo(StepStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(PipelineStep.class)
    void updateIfNoStepInProgress_anyStepAlreadyRunning_isRejectedAndLeavesTheRowAlone(PipelineStep holder) {
        RunId id = newRunId();
        EodPipelineRun saved = runs.insert(pendingRun(id, LocalDate.of(2026, 6, 18)));
        runs.update(saved.startingStep(holder));

        EodPipelineRun contender = saved.startingStep(PipelineStep.EVALUATE);

        assertThat(runs.updateIfNoStepInProgress(contender)).isEmpty();
        assertThat(runs.findById(id).orElseThrow().stepInProgress()).contains(holder);
    }

    @Test
    void updateIfNoStepInProgress_unknownRun_isRejected() {
        EodPipelineRun absent = pendingRun(newRunId(), LocalDate.of(2026, 6, 19));

        assertThat(runs.updateIfNoStepInProgress(absent.startingStep(PipelineStep.SYMBOLS))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"SUCCEEDED", "FAILED"})
    void updateIfRunTerminal_settledRun_appliesTheUpdate(RunStatus terminalStatus) {
        RunId id = newRunId();
        runs.insert(pendingRun(id, LocalDate.of(2026, 6, 21)));
        runs.update(withTerminalStatus(runs.findById(id).orElseThrow(), terminalStatus, NOW.plusSeconds(60)));

        EodPipelineRun restarted = runs.findById(id).orElseThrow().restartingFrom(PipelineStep.PRICES);

        assertThat(runs.updateIfRunTerminal(restarted)).contains(restarted);
        assertThat(runs.findById(id).orElseThrow().status()).isEqualTo(RunStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"PENDING", "IN_PROGRESS"})
    void updateIfRunTerminal_runStillAdvancing_isRejectedEvenWithNoStepInProgress(RunStatus activeStatus) {
        RunId id = newRunId();
        LocalDate runDate = LocalDate.of(2026, 6, 23);
        EodPipelineRun saved = runs.insert(pendingRun(id, runDate));
        runs.update(new EodPipelineRun(
                id, runDate, saved.trigger(), activeStatus, NOW, null,
                StepStatus.SUCCEEDED, StepStatus.PENDING, StepStatus.PENDING, null));

        EodPipelineRun restarted = runs.findById(id).orElseThrow().restartingFrom(PipelineStep.SYMBOLS);

        assertThat(runs.updateIfRunTerminal(restarted)).isEmpty();
        assertThat(runs.findById(id).orElseThrow().stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
    }

    @Test
    void updateIfRunTerminal_reactivatingCollidesWithAnotherActiveRunForSameDate_throwsRunAlreadyActive() {
        LocalDate runDate = LocalDate.of(2026, 6, 24);
        RunId firstId = newRunId();
        EodPipelineRun first = runs.insert(pendingRun(firstId, runDate));
        runs.update(withTerminalStatus(first, RunStatus.FAILED, NOW.plusSeconds(60)));
        runs.insert(pendingRun(newRunId(), runDate));

        EodPipelineRun restarted = runs.findById(firstId).orElseThrow().restartingFrom(PipelineStep.SYMBOLS);

        assertThatThrownBy(() -> runs.updateIfRunTerminal(restarted))
                .isInstanceOf(RunAlreadyActiveException.class)
                .extracting("runDate")
                .isEqualTo(runDate);
    }

    @Test
    void failNonTerminalRuns_runStrandedInProgress_failsTheRunAndItsRunningStep() {
        RunId id = newRunId();
        LocalDate runDate = LocalDate.of(2026, 6, 25);
        EodPipelineRun saved = runs.insert(pendingRun(id, runDate));
        runs.update(saved.startingStep(PipelineStep.PRICES));

        int failed = runs.failNonTerminalRuns(NOW.plusSeconds(300), "interrupted by restart");

        assertThat(failed).isEqualTo(1);
        EodPipelineRun found = runs.findById(id).orElseThrow();
        assertThat(found.status()).isEqualTo(RunStatus.FAILED);
        assertThat(found.stepPricesStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(found.errorMessage()).isEqualTo("interrupted by restart");
    }

    @Test
    void failNonTerminalRuns_stepsNotRunning_keepTheirRecordedStatus() {
        RunId id = newRunId();
        LocalDate runDate = LocalDate.of(2026, 6, 26);
        EodPipelineRun saved = runs.insert(pendingRun(id, runDate));
        runs.update(new EodPipelineRun(
                id, runDate, saved.trigger(), RunStatus.IN_PROGRESS, NOW, null,
                StepStatus.SUCCEEDED, StepStatus.IN_PROGRESS, StepStatus.PENDING, null));

        runs.failNonTerminalRuns(NOW.plusSeconds(300), "interrupted by restart");

        EodPipelineRun found = runs.findById(id).orElseThrow();
        assertThat(found.stepSymbolsStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(found.stepEvaluateStatus()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    void failNonTerminalRuns_clearingAStrandedRun_freesTheRunDateForANewRun() {
        LocalDate runDate = LocalDate.of(2026, 6, 27);
        EodPipelineRun stranded = runs.insert(pendingRun(newRunId(), runDate));
        runs.update(stranded.startingStep(PipelineStep.SYMBOLS));

        runs.failNonTerminalRuns(NOW.plusSeconds(300), "interrupted by restart");

        EodPipelineRun fresh = runs.insert(pendingRun(newRunId(), runDate));
        assertThat(runs.findById(fresh.id())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"SUCCEEDED", "FAILED"})
    void failNonTerminalRuns_alreadySettledRuns_areLeftUntouched(RunStatus terminalStatus) {
        RunId id = newRunId();
        runs.insert(pendingRun(id, LocalDate.of(2026, 6, 28)));
        runs.update(withTerminalStatus(runs.findById(id).orElseThrow(), terminalStatus, NOW.plusSeconds(60)));

        int failed = runs.failNonTerminalRuns(NOW.plusSeconds(300), "interrupted by restart");

        assertThat(failed).isZero();
        assertThat(runs.findById(id).orElseThrow().status()).isEqualTo(terminalStatus);
    }

    private static EodPipelineRun pendingRun(RunId id, LocalDate runDate) {
        return new EodPipelineRun(
                id, runDate, Trigger.CRON, RunStatus.PENDING, NOW, null,
                StepStatus.PENDING, StepStatus.PENDING, StepStatus.PENDING, null);
    }

    private static EodPipelineRun withTerminalStatus(EodPipelineRun run, RunStatus terminalStatus, Instant finishedAt) {
        return new EodPipelineRun(
                run.id(), run.runDate(), run.trigger(), terminalStatus, run.startedAt(), finishedAt,
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null);
    }

    private static RunId newRunId() {
        return new RunId(UUID.randomUUID());
    }
}
