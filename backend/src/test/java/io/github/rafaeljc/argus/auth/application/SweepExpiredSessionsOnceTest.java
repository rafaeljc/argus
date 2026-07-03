package io.github.rafaeljc.argus.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SweepExpiredSessionsOnceTest {

    private static final Instant NOW = Instant.parse("2026-07-03T12:00:00Z");
    private static final int BATCH_SIZE = SweepExpiredSessionsOnce.BATCH_SIZE;

    @Mock
    private SessionRepository sessionRepository;

    @Test
    void execute_stopsAfterOneBatchWhenRepositoryReturnsLessThanBatchSize() {
        when(sessionRepository.deleteExpiredBefore(NOW, BATCH_SIZE)).thenReturn(BATCH_SIZE - 1);
        SweepExpiredSessionsOnce useCase = new SweepExpiredSessionsOnce(sessionRepository, new FixedClock(NOW));

        useCase.execute();

        verify(sessionRepository, times(1)).deleteExpiredBefore(NOW, BATCH_SIZE);
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void execute_stopsAfterEmptyFirstBatch() {
        when(sessionRepository.deleteExpiredBefore(NOW, BATCH_SIZE)).thenReturn(0);
        SweepExpiredSessionsOnce useCase = new SweepExpiredSessionsOnce(sessionRepository, new FixedClock(NOW));

        useCase.execute();

        verify(sessionRepository, times(1)).deleteExpiredBefore(NOW, BATCH_SIZE);
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void execute_loopsUntilBatchIsShort() {
        when(sessionRepository.deleteExpiredBefore(NOW, BATCH_SIZE))
                .thenReturn(BATCH_SIZE)
                .thenReturn(BATCH_SIZE)
                .thenReturn(BATCH_SIZE - 1);
        SweepExpiredSessionsOnce useCase = new SweepExpiredSessionsOnce(sessionRepository, new FixedClock(NOW));

        useCase.execute();

        verify(sessionRepository, times(3)).deleteExpiredBefore(NOW, BATCH_SIZE);
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void execute_propagatesRuntimeExceptionFromRepository() {
        RuntimeException boom = new RuntimeException("db down");
        when(sessionRepository.deleteExpiredBefore(NOW, BATCH_SIZE)).thenThrow(boom);
        SweepExpiredSessionsOnce useCase = new SweepExpiredSessionsOnce(sessionRepository, new FixedClock(NOW));

        assertThatThrownBy(useCase::execute).isSameAs(boom);
    }
}
