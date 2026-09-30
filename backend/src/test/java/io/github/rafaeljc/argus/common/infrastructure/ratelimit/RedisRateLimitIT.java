package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.rafaeljc.argus.common.application.ratelimit.ConsumptionResult;
import io.github.rafaeljc.argus.common.application.ratelimit.RateLimiter;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({PostgresContainer.class, RedisContainer.class})
@SpringBootTest
class RedisRateLimitIT {

    private static final String BUCKET = "shared";
    private static final BucketDefinition DEFINITION = new BucketDefinition(3L, 3L, Duration.ofMinutes(1));

    @Autowired
    private RedisClient redisClient;

    // Simulates two ECS tasks: each gets its own connection and ProxyManager, built through the
    // same production wiring (RateLimitConfig), sharing nothing except the Redis container.
    @Test
    void tryConsume_twoIndependentProxyManagersOverSameRedis_shareBucketState() {
        RateLimiter taskA = newLimiter();
        RateLimiter taskB = newLimiter();

        for (int i = 0; i < 3; i++) {
            ConsumptionResult result = taskA.tryConsume(BUCKET, "203.0.113.9");
            assertThat(result.allowed()).isTrue();
        }

        ConsumptionResult onTaskB = taskB.tryConsume(BUCKET, "203.0.113.9");

        assertThat(onTaskB.allowed()).isFalse();
        assertThat(onTaskB.secondsUntilRefill()).isPositive();
    }

    @Test
    void tryConsume_distinctKeys_remainIndependentAcrossInstances() {
        RateLimiter taskA = newLimiter();
        RateLimiter taskB = newLimiter();

        for (int i = 0; i < 3; i++) {
            taskA.tryConsume(BUCKET, "203.0.113.9");
        }

        ConsumptionResult onTaskB = taskB.tryConsume(BUCKET, "203.0.113.10");

        assertThat(onTaskB.allowed()).isTrue();
        assertThat(onTaskB.remainingTokens()).isEqualTo(2L);
    }

    private RateLimiter newLimiter() {
        RateLimitConfig config = new RateLimitConfig();
        ProxyManager<String> proxyManager = config.rateLimitProxyManager(redisClient);
        RateLimitProperties properties = new RateLimitProperties(Map.of(BUCKET, DEFINITION));
        return new Bucket4jRateLimiter(properties, proxyManager);
    }
}
