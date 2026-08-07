package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.RunId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunNotSettledExceptionTest {

    @Test
    void exposesWireCodeStatusRunIdAndRunStatus() {
        RunId runId = new RunId(UUID.randomUUID());

        RunNotSettledException ex = new RunNotSettledException(runId, RunStatus.IN_PROGRESS);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("CONFLICT");
        assertThat(ex.status()).isEqualTo(409);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.runId()).isEqualTo(runId);
        assertThat(ex.runStatus()).isEqualTo(RunStatus.IN_PROGRESS);
    }

    @Test
    void messageNamesTheStatusThatBlockedTheRerun() {
        RunNotSettledException ex = new RunNotSettledException(new RunId(UUID.randomUUID()), RunStatus.PENDING);

        assertThat(ex.getMessage()).contains("pending");
    }
}
