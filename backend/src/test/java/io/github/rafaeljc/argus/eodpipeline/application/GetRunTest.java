package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.ResourceNotFoundException;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetRunTest {

    private static final RunId RUN_ID = new RunId(UUID.randomUUID());

    @Mock
    private EodPipelineRunRepository repository;

    private GetRun getRun;

    @BeforeEach
    void setUp() {
        getRun = new GetRun(repository);
    }

    @Test
    void get_found_returnsRun() {
        EodPipelineRun run = new EodPipelineRun(
                RUN_ID, LocalDate.parse("2026-06-22"), Trigger.CRON, RunStatus.SUCCEEDED,
                Instant.parse("2026-06-22T21:30:00Z"), Instant.parse("2026-06-22T21:35:00Z"),
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null);
        when(repository.findById(RUN_ID)).thenReturn(Optional.of(run));

        EodPipelineRun result = getRun.get(RUN_ID);

        assertThat(result).isEqualTo(run);
    }

    @Test
    void get_missing_throwsResourceNotFound() {
        when(repository.findById(RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getRun.get(RUN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
