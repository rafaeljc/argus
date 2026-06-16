package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    private static final class TestDomainException extends DomainException {
        TestDomainException(String message) {
            super(message);
        }

        TestDomainException(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String code() {
            return "TEST_CODE";
        }
    }

    @Test
    void defaultStatus_is422() {
        DomainException ex = new TestDomainException("boom");

        assertThat(ex.status()).isEqualTo(422);
    }

    @Test
    void defaultDetails_isEmptyList() {
        DomainException ex = new TestDomainException("boom");

        assertThat(ex.details()).isEqualTo(List.of());
    }

    @Test
    void messageConstructor_propagatesMessage() {
        DomainException ex = new TestDomainException("boom");

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor_propagatesBoth() {
        Throwable cause = new IllegalStateException("upstream");
        DomainException ex = new TestDomainException("boom", cause);

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void isRuntimeException() {
        assertThat(new TestDomainException("boom")).isInstanceOf(RuntimeException.class);
    }
}
