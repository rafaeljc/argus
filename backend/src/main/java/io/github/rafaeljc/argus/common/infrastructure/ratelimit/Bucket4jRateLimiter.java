package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.rafaeljc.argus.common.application.ratelimit.ConsumptionResult;
import io.github.rafaeljc.argus.common.application.ratelimit.RateLimiter;
import java.util.Map;
import java.util.Objects;

public class Bucket4jRateLimiter implements RateLimiter {

    private static final long ONE_SECOND_IN_NANOS = 1_000_000_000L;

    private final Map<String, BucketConfiguration> configurations;
    private final ProxyManager<String> proxyManager;

    public Bucket4jRateLimiter(RateLimitProperties properties, ProxyManager<String> proxyManager) {
        Objects.requireNonNull(properties, "properties");
        this.proxyManager = Objects.requireNonNull(proxyManager, "proxyManager");
        this.configurations = properties.buckets().entrySet().stream()
                .collect(toUnmodifiableMap(Map.Entry::getKey, entry -> toConfiguration(entry.getValue())));
    }

    @Override
    public ConsumptionResult tryConsume(String bucketName, String key) {
        BucketConfiguration configuration = configurations.get(bucketName);
        if (configuration == null) {
            throw new IllegalArgumentException("unknown rate-limit bucket: " + bucketName);
        }
        String compositeKey = bucketName + ":" + key;
        long limit = configuration.getBandwidths()[0].getCapacity();
        ConsumptionProbe probe = proxyManager.builder()
                .build(compositeKey, () -> configuration)
                .tryConsumeAndReturnRemaining(1L);
        return new ConsumptionResult(
                probe.isConsumed(),
                limit,
                probe.getRemainingTokens(),
                nanosToCeilSeconds(probe.getNanosToWaitForRefill()),
                nanosToCeilSeconds(probe.getNanosToWaitForReset()));
    }

    private static BucketConfiguration toConfiguration(BucketDefinition definition) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(definition.capacity())
                .refillGreedy(definition.refillTokens(), definition.refillDuration())
                .build();
        return BucketConfiguration.builder().addLimit(bandwidth).build();
    }

    private static long nanosToCeilSeconds(long nanos) {
        if (nanos <= 0L) {
            return 0L;
        }
        return (nanos + ONE_SECOND_IN_NANOS - 1L) / ONE_SECOND_IN_NANOS;
    }
}
