package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceUnavailableExceptionTest {

    @Test
    void code_is_serviceUnavailable() {
        ServiceUnavailableException ex = new ServiceUnavailableException("vendor marketdata unavailable");

        assertThat(ex.code()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void statusIs_503() {
        ServiceUnavailableException ex = new ServiceUnavailableException("vendor marketdata unavailable");

        assertThat(ex.status()).isEqualTo(503);
    }

    @Test
    void message_is_preserved() {
        ServiceUnavailableException ex = new ServiceUnavailableException("vendor marketdata unavailable");

        assertThat(ex.getMessage()).isEqualTo("vendor marketdata unavailable");
    }
}
