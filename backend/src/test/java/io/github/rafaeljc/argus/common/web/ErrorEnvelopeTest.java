package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorEnvelopeTest {

    @Test
    void constructor_validArgs_storesError() {
        ApiError err = new ApiError("CODE", "message", List.of());

        ErrorEnvelope envelope = new ErrorEnvelope(err);

        assertThat(envelope.error()).isEqualTo(err);
    }

    @Test
    void constructor_nullError_throwsIllegalArgument() {
        assertThatThrownBy(() -> new ErrorEnvelope(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error");
    }
}
