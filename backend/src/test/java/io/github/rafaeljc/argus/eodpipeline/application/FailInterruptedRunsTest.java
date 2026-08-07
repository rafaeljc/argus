package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailInterruptedRunsTest {

    private static final Instant NOW = Instant.parse("2026-06-22T21:00:00Z");

    @Mock
    private EodPipelineRunRepository runs;

    private FailInterruptedRuns failInterruptedRuns;

    @BeforeEach
    void setUp() {
        failInterruptedRuns = new FailInterruptedRuns(runs, new FixedClock(NOW));
    }

    @Test
    void execute_runsLeftInProgress_failsThemStampedWithTheCurrentTime() {
        when(runs.failNonTerminalRuns(NOW, "interrupted by restart")).thenReturn(2);

        assertThat(failInterruptedRuns.execute()).isEqualTo(2);
    }

    @Test
    void execute_nothingLeftInProgress_reportsNoRowsAffected() {
        when(runs.failNonTerminalRuns(NOW, "interrupted by restart")).thenReturn(0);

        assertThat(failInterruptedRuns.execute()).isZero();
    }
}
