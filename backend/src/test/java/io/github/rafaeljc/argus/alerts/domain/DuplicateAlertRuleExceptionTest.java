package io.github.rafaeljc.argus.alerts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.Percentage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateAlertRuleExceptionTest {

    @Test
    void exposesWireCodeStatusAndSignature() {
        Percentage threshold = new Percentage(new BigDecimal("5.0"));
        AlertLookbackWindow window = new AlertLookbackWindow(30);
        DuplicateAlertRuleException ex = new DuplicateAlertRuleException(Direction.UP, threshold, window);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("DUPLICATE_RULE");
        assertThat(ex.status()).isEqualTo(409);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.direction()).isEqualTo(Direction.UP);
        assertThat(ex.threshold()).isEqualTo(threshold);
        assertThat(ex.window()).isEqualTo(window);
    }
}
