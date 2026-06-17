package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SuccessEnvelopeTest {

    @Test
    void constructor_validArgs_storesData() {
        SuccessEnvelope<String> envelope = new SuccessEnvelope<>("hello");

        assertThat(envelope.data()).isEqualTo("hello");
    }

    @Test
    void constructor_nullData_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SuccessEnvelope<>(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");
    }
}
