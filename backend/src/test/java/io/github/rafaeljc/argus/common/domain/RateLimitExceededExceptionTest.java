package io.github.rafaeljc.argus.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitExceededExceptionTest {

    @Test
    void exposesWireCodeStatusAndRetryAfter() {
        RateLimitExceededException ex = new RateLimitExceededException(30L);

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(ex.status()).isEqualTo(429);
        assertThat(ex.details()).isEqualTo(List.of());
        assertThat(ex.retryAfterSeconds()).isEqualTo(30L);
    }

    @Test
    void constructor_negativeRetryAfter_throwsIllegalArgument() {
        assertThatThrownBy(() -> new RateLimitExceededException(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
