package io.github.rafaeljc.argus.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TooManyAlertRulesExceptionTest {

    @Test
    void exposesWireCodeAndStatus() {
        TooManyAlertRulesException ex = new TooManyAlertRulesException(20);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("TOO_MANY_RULES");
        assertThat(ex.status()).isEqualTo(422);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.limit()).isEqualTo(20);
    }
}
