package io.github.rafaeljc.argus.common.application.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConsumptionResultTest {

    @Test
    void construct_validAllowed_exposesFields() {
        ConsumptionResult result = new ConsumptionResult(true, 60L, 59L, 0L, 30L);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(60L);
        assertThat(result.remainingTokens()).isEqualTo(59L);
        assertThat(result.secondsUntilRefill()).isZero();
        assertThat(result.secondsUntilReset()).isEqualTo(30L);
    }

    @Test
    void construct_validRejected_exposesFields() {
        ConsumptionResult result = new ConsumptionResult(false, 60L, 0L, 10L, 60L);

        assertThat(result.allowed()).isFalse();
        assertThat(result.secondsUntilRefill()).isEqualTo(10L);
        assertThat(result.remainingTokens()).isZero();
    }

    @Test
    void construct_negativeLimit_throws() {
        assertThatThrownBy(() -> new ConsumptionResult(true, -1L, 0L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void construct_negativeRemaining_throws() {
        assertThatThrownBy(() -> new ConsumptionResult(true, 1L, -1L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remainingTokens");
    }

    @Test
    void construct_negativeSecondsUntilRefill_throws() {
        assertThatThrownBy(() -> new ConsumptionResult(true, 1L, 0L, -1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secondsUntilRefill");
    }

    @Test
    void construct_negativeSecondsUntilReset_throws() {
        assertThatThrownBy(() -> new ConsumptionResult(true, 1L, 0L, 0L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secondsUntilReset");
    }
}
