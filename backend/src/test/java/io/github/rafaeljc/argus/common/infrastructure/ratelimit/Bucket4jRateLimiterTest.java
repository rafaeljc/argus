package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Bucket4jRateLimiterTest {

    private static final String BUCKET = "RL.test";
    private static final BucketDefinition DEFINITION =
            new BucketDefinition(3L, 3L, Duration.ofMinutes(1));

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(Map.of(BUCKET, DEFINITION));
        CaffeineProxyManager<String> proxyManager =
                new CaffeineProxyManager<>(Caffeine.newBuilder(), Duration.ofMinutes(5));
        limiter = new Bucket4jRateLimiter(properties, proxyManager);
    }

    @Test
    void tryConsume_firstCall_returnsAllowedWithFullLimitMinusOne() {
        ConsumptionResult result = limiter.tryConsume(BUCKET, "user-1");

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(3L);
        assertThat(result.remainingTokens()).isEqualTo(2L);
        assertThat(result.secondsUntilRefill()).isZero();
    }

    @Test
    void tryConsume_repeatedCalls_decrementRemainingTokens() {
        limiter.tryConsume(BUCKET, "user-1");
        limiter.tryConsume(BUCKET, "user-1");
        ConsumptionResult third = limiter.tryConsume(BUCKET, "user-1");

        assertThat(third.remainingTokens()).isZero();
        assertThat(third.allowed()).isTrue();
    }

    @Test
    void tryConsume_exhausted_returnsRejectedWithPositiveRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume(BUCKET, "user-1");
        }

        ConsumptionResult blocked = limiter.tryConsume(BUCKET, "user-1");

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remainingTokens()).isZero();
        assertThat(blocked.secondsUntilRefill()).isPositive();
    }

    @Test
    void tryConsume_differentKeysOnSameBucket_areIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume(BUCKET, "user-1");
        }

        ConsumptionResult freshUser = limiter.tryConsume(BUCKET, "user-2");

        assertThat(freshUser.allowed()).isTrue();
        assertThat(freshUser.remainingTokens()).isEqualTo(2L);
    }

    @Test
    void tryConsume_sameKeyAcrossBuckets_areIndependent() {
        BucketDefinition other = new BucketDefinition(1L, 1L, Duration.ofMinutes(1));
        RateLimitProperties properties = new RateLimitProperties(Map.of(BUCKET, DEFINITION, "RL.other", other));
        CaffeineProxyManager<String> proxyManager =
                new CaffeineProxyManager<>(Caffeine.newBuilder(), Duration.ofMinutes(5));
        RateLimiter twoBucketLimiter = new Bucket4jRateLimiter(properties, proxyManager);
        twoBucketLimiter.tryConsume("RL.other", "shared-key");

        ConsumptionResult onTestBucket = twoBucketLimiter.tryConsume(BUCKET, "shared-key");

        assertThat(onTestBucket.allowed()).isTrue();
        assertThat(onTestBucket.remainingTokens()).isEqualTo(2L);
    }

    @Test
    void tryConsume_unknownBucket_throws() {
        assertThatThrownBy(() -> limiter.tryConsume("RL.unknown", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RL.unknown");
    }

}
