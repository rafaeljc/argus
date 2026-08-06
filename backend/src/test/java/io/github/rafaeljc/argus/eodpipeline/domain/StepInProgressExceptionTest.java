package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.RunId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepInProgressExceptionTest {

    @Test
    void exposesWireCodeStatusRunIdAndStep() {
        RunId runId = new RunId(UUID.randomUUID());

        StepInProgressException ex = new StepInProgressException(runId, PipelineStep.PRICES);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("CONFLICT");
        assertThat(ex.status()).isEqualTo(409);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.runId()).isEqualTo(runId);
        assertThat(ex.step()).isEqualTo(PipelineStep.PRICES);
    }
}
