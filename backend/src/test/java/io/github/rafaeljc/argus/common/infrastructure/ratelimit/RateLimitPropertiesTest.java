package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    private static final BucketDefinition SIGNUP =
            new BucketDefinition(5L, 5L, Duration.ofHours(1));
    private static final BucketDefinition LOGIN =
            new BucketDefinition(10L, 10L, Duration.ofMinutes(15));

    @Test
    void construct_nullMap_normalizesToEmpty() {
        RateLimitProperties properties = new RateLimitProperties(null);

        assertThat(properties.buckets()).isEmpty();
    }

    @Test
    void construct_emptyMap_isEmpty() {
        RateLimitProperties properties = new RateLimitProperties(Map.of());

        assertThat(properties.buckets()).isEmpty();
    }

    @Test
    void construct_populatedMap_exposesBuckets() {
        RateLimitProperties properties = new RateLimitProperties(
                Map.of("RL.auth.signup", SIGNUP, "RL.auth.login", LOGIN));

        assertThat(properties.buckets())
                .hasSize(2)
                .containsEntry("RL.auth.signup", SIGNUP)
                .containsEntry("RL.auth.login", LOGIN);
    }

    @Test
    void buckets_returnedMapIsImmutable() {
        Map<String, BucketDefinition> mutable = new HashMap<>();
        mutable.put("RL.auth.signup", SIGNUP);
        RateLimitProperties properties = new RateLimitProperties(mutable);

        assertThatThrownBy(() -> properties.buckets().put("RL.auth.login", LOGIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void buckets_inputMutationDoesNotLeakIntoProperties() {
        Map<String, BucketDefinition> mutable = new HashMap<>();
        mutable.put("RL.auth.signup", SIGNUP);
        RateLimitProperties properties = new RateLimitProperties(mutable);

        mutable.put("RL.auth.login", LOGIN);

        assertThat(properties.buckets()).hasSize(1).containsOnlyKeys("RL.auth.signup");
    }
}
