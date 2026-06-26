package io.github.rafaeljc.argus.email.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SendResultTest {

    @Test
    void construct_successWithNullErrorMessage_returnsRecord() {
        SendResult result = new SendResult(true, null);

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void construct_successWithErrorMessage_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SendResult(true, "some failure"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }

    @Test
    void construct_failureWithErrorMessage_returnsRecord() {
        SendResult result = new SendResult(false, "vendor 500");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("vendor 500");
    }

    @Test
    void construct_failureWithNullErrorMessage_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SendResult(false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }

    @Test
    void construct_failureWithBlankErrorMessage_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SendResult(false, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }
}
