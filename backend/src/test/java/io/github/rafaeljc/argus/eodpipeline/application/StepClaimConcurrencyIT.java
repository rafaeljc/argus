package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.application.port.RunDispatcher;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.PipelineStep;
import io.github.rafaeljc.argus.eodpipeline.domain.RunNotSettledException;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepInProgressException;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// The point of splitting a step into claim / work / settle is that the work holds no transaction
// and the claim is already committed while it runs. These tests pin both halves of that: a
// competing rerun must be rejected immediately rather than queued behind the work.
@Import({PostgresContainer.class, StepClaimConcurrencyIT.NoopDispatcherConfig.class})
@SpringBootTest
class StepClaimConcurrencyIT {

    private static final Instant STARTED_AT = Instant.parse("2026-07-01T21:00:00Z");
    private static final Duration MUST_NOT_BLOCK = Duration.ofSeconds(10);

    @Autowired
    private StepExecution stepExecution;

    @Autowired
    private StepLifecycle lifecycle;

    @Autowired
    private EodPipelineService service;

    @Autowired
    private EodPipelineRunRepository runs;

    @Test
    void stepWork_runsWithNoTransactionOpen() {
        RunId id = seedFailedRun(LocalDate.of(2026, 7, 2));
        AtomicBoolean transactionActive = new AtomicBoolean(true);

        stepExecution.run(id, PipelineStep.PRICES, run -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return StepOutcome.success();
        });

        assertThat(transactionActive).isFalse();
    }

    // Reads from another thread on purpose: a same-thread read would reuse the caller's connection
    // and see its own uncommitted writes, which is precisely the illusion this guards against.
    @Test
    void stepWork_claimIsVisibleToOtherConnectionsWhileTheWorkRuns() {
        RunId id = seedFailedRun(LocalDate.of(2026, 7, 3));

        stepExecution.run(id, PipelineStep.PRICES, run -> {
            EodPipelineRun seenElsewhere = onAnotherConnection(() -> runs.findById(id).orElseThrow());
            assertThat(seenElsewhere.stepPricesStatus()).isEqualTo(StepStatus.IN_PROGRESS);
            assertThat(seenElsewhere.status()).isEqualTo(RunStatus.IN_PROGRESS);
            return StepOutcome.success();
        });
    }

    private static <T> T onAnotherConnection(Supplier<T> read) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return CompletableFuture.supplyAsync(read, pool).get(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rerun_whileAStepIsRunning_isRejectedImmediatelyInsteadOfWaitingForTheStep() throws Exception {
        RunId id = seedFailedRun(LocalDate.of(2026, 7, 4));
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            final CompletableFuture<Void> step = CompletableFuture.runAsync(
                    () -> stepExecution.run(id, PipelineStep.PRICES, run -> {
                        workStarted.countDown();
                        awaitUninterruptibly(releaseWork);
                        return StepOutcome.success();
                    }),
                    pool);

            assertThat(workStarted.await(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS)).isTrue();

            // Fails against the advisory-lock version, which blocks here until releaseWork fires.
            assertThatThrownBy(() -> service.rerunFromStep(id, PipelineStep.PRICES, new UserId(UUID.randomUUID())))
                    .isInstanceOf(RunNotSettledException.class);

            releaseWork.countDown();
            step.get(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS);
        } finally {
            releaseWork.countDown();
            pool.shutdownNow();
        }

        assertThat(runs.findById(id).orElseThrow().stepPricesStatus()).isEqualTo(StepStatus.SUCCEEDED);
    }

    @Test
    void claim_whileAnotherStepIsRunning_isRejectedImmediatelyInsteadOfWaitingForIt() throws Exception {
        RunId id = seedFailedRun(LocalDate.of(2026, 7, 5));
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            final CompletableFuture<Void> step = CompletableFuture.runAsync(
                    () -> stepExecution.run(id, PipelineStep.SYMBOLS, run -> {
                        workStarted.countDown();
                        awaitUninterruptibly(releaseWork);
                        return StepOutcome.success();
                    }),
                    pool);

            assertThat(workStarted.await(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> lifecycle.claim(id, PipelineStep.EVALUATE))
                    .isInstanceOf(StepInProgressException.class)
                    .extracting("step")
                    .isEqualTo(PipelineStep.SYMBOLS);

            releaseWork.countDown();
            step.get(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS);
        } finally {
            releaseWork.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void twoConcurrentClaimsOfTheSameStep_onlyOneWins() throws Exception {
        RunId id = seedFailedRun(LocalDate.of(2026, 7, 6));
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> claimSucceeds(id), pool);
            CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> claimSucceeds(id), pool);
            CompletableFuture.allOf(first, second).get(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS);

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean claimSucceeds(RunId id) {
        try {
            lifecycle.claim(id, PipelineStep.PRICES);
            return true;
        } catch (StepInProgressException e) {
            return false;
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(MUST_NOT_BLOCK.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private RunId seedFailedRun(LocalDate runDate) {
        RunId id = new RunId(UUID.randomUUID());
        runs.insert(new EodPipelineRun(
                id, runDate, Trigger.CRON, RunStatus.FAILED, STARTED_AT, STARTED_AT.plusSeconds(60),
                StepStatus.SUCCEEDED, StepStatus.FAILED, StepStatus.SKIPPED, "boom"));
        return id;
    }

    @TestConfiguration
    static class NoopDispatcherConfig {

        // Keeps a successful rerun from handing the run to the real executor mid-test.
        @Bean
        @Primary
        RunDispatcher noopRunDispatcher() {
            return new RunDispatcher() {
                @Override
                public void dispatch(RunId id) {
                    // no-op
                }

                @Override
                public void dispatchFrom(RunId id, PipelineStep entryStep) {
                    // no-op
                }
            };
        }
    }
}
