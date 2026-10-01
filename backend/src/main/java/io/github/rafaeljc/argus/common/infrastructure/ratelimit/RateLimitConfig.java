package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.github.rafaeljc.argus.common.application.ratelimit.RateLimiter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    // Bucket state is evicted from Redis this long after it would fully refill, to bound key
    // growth while still allowing back-to-back requests within the window to share a bucket.
    private static final Duration KEEP_AFTER_REFILL = Duration.ofMinutes(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    ProxyManager<String> rateLimitProxyManager(RedisClient redisClient) {
        // redisClient (RedisClientConfig) is owned by the application, not borrowed from Spring
        // Session's connection factory, so this connection's validity doesn't depend on that
        // factory's own Lifecycle. No destroyMethod here: RedisClient.shutdown() closes every
        // connection opened from it, so the client's own destruction covers this one.
        StatefulRedisConnection<String, byte[]> connection =
                redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        ClientSideConfig clientSideConfig = ClientSideConfig.getDefault()
                .withRequestTimeout(REQUEST_TIMEOUT)
                .withExpirationAfterWriteStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_AFTER_REFILL));
        return LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(clientSideConfig)
                .build();
    }

    @Bean
    RateLimiter rateLimiter(RateLimitProperties properties, ProxyManager<String> rateLimitProxyManager) {
        return new Bucket4jRateLimiter(properties, rateLimitProxyManager);
    }
}
