package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BucketDefinitionTest {

    @Test
    void construct_validValues_exposesAllFields() {
        BucketDefinition definition = new BucketDefinition(60L, 60L, Duration.ofMinutes(1));

        assertThat(definition.capacity()).isEqualTo(60L);
        assertThat(definition.refillTokens()).isEqualTo(60L);
        assertThat(definition.refillDuration()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void construct_zeroCapacity_throws() {
        assertThatThrownBy(() -> new BucketDefinition(0L, 1L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void construct_negativeCapacity_throws() {
        assertThatThrownBy(() -> new BucketDefinition(-1L, 1L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void construct_zeroRefillTokens_throws() {
        assertThatThrownBy(() -> new BucketDefinition(1L, 0L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillTokens");
    }

    @Test
    void construct_negativeRefillTokens_throws() {
        assertThatThrownBy(() -> new BucketDefinition(1L, -1L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillTokens");
    }

    @Test
    void construct_nullRefillDuration_throws() {
        assertThatThrownBy(() -> new BucketDefinition(1L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillDuration");
    }

    @Test
    void construct_zeroRefillDuration_throws() {
        assertThatThrownBy(() -> new BucketDefinition(1L, 1L, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillDuration");
    }

    @Test
    void construct_negativeRefillDuration_throws() {
        assertThatThrownBy(() -> new BucketDefinition(1L, 1L, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refillDuration");
    }
}
