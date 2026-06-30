package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

public interface RateLimiter {

    ConsumptionResult tryConsume(String bucketName, String key);
}
