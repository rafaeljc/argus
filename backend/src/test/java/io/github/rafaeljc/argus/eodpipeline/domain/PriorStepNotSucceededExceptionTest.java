package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.RunId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriorStepNotSucceededExceptionTest {

    @Test
    void exposesWireCodeStatusRunIdEntryStepBlockingStepAndBlockingStatus() {
        RunId runId = new RunId(UUID.randomUUID());

        PriorStepNotSucceededException ex = new PriorStepNotSucceededException(
                runId, PipelineStep.EVALUATE, PipelineStep.PRICES, StepStatus.FAILED);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("CONFLICT");
        assertThat(ex.status()).isEqualTo(409);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.runId()).isEqualTo(runId);
        assertThat(ex.entryStep()).isEqualTo(PipelineStep.EVALUATE);
        assertThat(ex.blockingStep()).isEqualTo(PipelineStep.PRICES);
        assertThat(ex.blockingStatus()).isEqualTo(StepStatus.FAILED);
    }

    @Test
    void messageNamesTheEntryStepBlockingStepAndBlockingStatus() {
        PriorStepNotSucceededException ex = new PriorStepNotSucceededException(
                new RunId(UUID.randomUUID()), PipelineStep.EVALUATE, PipelineStep.PRICES, StepStatus.FAILED);

        assertThat(ex.getMessage()).contains("evaluate").contains("prices").contains("failed");
    }
}
