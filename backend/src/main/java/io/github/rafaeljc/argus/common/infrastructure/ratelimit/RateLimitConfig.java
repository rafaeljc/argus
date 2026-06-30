package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    // Bucket state is evicted from Caffeine this long after it would fully refill, to bound
    // memory while still allowing back-to-back requests within the window to share a bucket.
    private static final Duration KEEP_AFTER_REFILL = Duration.ofMinutes(10);

    @Bean
    ProxyManager<String> rateLimitProxyManager() {
        return new CaffeineProxyManager<>(Caffeine.newBuilder(), KEEP_AFTER_REFILL);
    }

    @Bean
    RateLimiter rateLimiter(RateLimitProperties properties, ProxyManager<String> rateLimitProxyManager) {
        return new Bucket4jRateLimiter(properties, rateLimitProxyManager);
    }
}
