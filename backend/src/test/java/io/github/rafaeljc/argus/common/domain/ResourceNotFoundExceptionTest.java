package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceNotFoundExceptionTest {

    @Test
    void exposesWireCodeAndStatus() {
        ResourceNotFoundException ex = new ResourceNotFoundException("user 42 not found");

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("NOT_FOUND");
        assertThat(ex.status()).isEqualTo(404);
        assertThat(ex.details()).isEqualTo(List.of());
    }

    @Test
    void messagePropagatedToRuntimeException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("user 42 not found");

        assertThat(ex.getMessage()).isEqualTo("user 42 not found");
    }
}
