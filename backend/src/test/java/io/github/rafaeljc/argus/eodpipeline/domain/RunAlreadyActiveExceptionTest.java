package io.github.rafaeljc.argus.eodpipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunAlreadyActiveExceptionTest {

    @Test
    void exposesWireCodeStatusAndRunDate() {
        LocalDate runDate = LocalDate.of(2026, 6, 22);

        RunAlreadyActiveException ex = new RunAlreadyActiveException(runDate);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("CONFLICT");
        assertThat(ex.status()).isEqualTo(409);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.runDate()).isEqualTo(runDate);
    }
}
