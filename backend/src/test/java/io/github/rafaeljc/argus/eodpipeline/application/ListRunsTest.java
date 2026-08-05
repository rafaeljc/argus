package io.github.rafaeljc.argus.eodpipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.RunId;
import io.github.rafaeljc.argus.eodpipeline.application.port.EodPipelineRunRepository;
import io.github.rafaeljc.argus.eodpipeline.domain.EodPipelineRun;
import io.github.rafaeljc.argus.eodpipeline.domain.RunStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.StepStatus;
import io.github.rafaeljc.argus.eodpipeline.domain.Trigger;
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
class ListRunsTest {

    @Mock
    private EodPipelineRunRepository repository;

    private ListRuns listRuns;

    @BeforeEach
    void setUp() {
        listRuns = new ListRuns(repository);
    }

    @Test
    void list_delegatesPageAndPerPageToRepositoryAndReturnsItemsWithTotal() {
        EodPipelineRun run = new EodPipelineRun(
                new RunId(UUID.randomUUID()), LocalDate.parse("2026-06-22"), Trigger.CRON, RunStatus.SUCCEEDED,
                Instant.parse("2026-06-22T21:30:00Z"), Instant.parse("2026-06-22T21:35:00Z"),
                StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, StepStatus.SUCCEEDED, null);
        when(repository.listPaged(2, 25)).thenReturn(List.of(run));
        when(repository.count()).thenReturn(30);

        PageResult<EodPipelineRun> result = listRuns.list(2, 25);

        assertThat(result.items()).containsExactly(run);
        assertThat(result.total()).isEqualTo(30);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.perPage()).isEqualTo(25);
    }

    @Test
    void list_noRuns_returnsEmptyPageWithZeroTotal() {
        when(repository.listPaged(1, 50)).thenReturn(List.of());
        when(repository.count()).thenReturn(0);

        PageResult<EodPipelineRun> result = listRuns.list(1, 50);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
