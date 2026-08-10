package io.github.rafaeljc.argus.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditLogFilterTest {

    private static final Instant EARLIER = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-15T00:00:00Z");

    @Test
    void constructor_fromAfterTo_throwsIllegalArgument() {
        assertThatThrownBy(() -> new AuditLogFilter(null, null, null, LATER, EARLIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from")
                .hasMessageContaining("to");
    }

    @Test
    void constructor_fromEqualsTo_isAllowed() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, EARLIER, EARLIER);

        assertThat(filter.from()).isEqualTo(EARLIER);
        assertThat(filter.to()).isEqualTo(EARLIER);
    }

    @Test
    void constructor_onlyFromSet_isAllowed() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, EARLIER, null);

        assertThat(filter.from()).isEqualTo(EARLIER);
        assertThat(filter.to()).isNull();
    }
}
