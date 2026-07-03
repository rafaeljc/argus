package io.github.rafaeljc.argus.auth.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.github.rafaeljc.argus.auth.application.SweepExpiredSessionsOnce;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionSweeperSchedulerTest {

    @Mock
    private SweepExpiredSessionsOnce sweepExpiredSessionsOnce;

    @Test
    void sweep_delegatesToUseCase() {
        SessionSweeperScheduler scheduler = new SessionSweeperScheduler(sweepExpiredSessionsOnce);

        scheduler.sweep();

        verify(sweepExpiredSessionsOnce).execute();
    }

    @Test
    void sweep_swallowsRuntimeExceptionSoSchedulerThreadKeepsRunning() {
        doThrow(new RuntimeException("db down")).when(sweepExpiredSessionsOnce).execute();
        SessionSweeperScheduler scheduler = new SessionSweeperScheduler(sweepExpiredSessionsOnce);

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();
    }
}
