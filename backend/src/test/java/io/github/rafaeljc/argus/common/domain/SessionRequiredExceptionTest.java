package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionRequiredExceptionTest {

    @Test
    void exposesWireCodeAndStatus() {
        SessionRequiredException ex = new SessionRequiredException();

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("UNAUTHORIZED");
        assertThat(ex.status()).isEqualTo(401);
        assertThat(ex.details()).isEqualTo(List.of());
    }
}
