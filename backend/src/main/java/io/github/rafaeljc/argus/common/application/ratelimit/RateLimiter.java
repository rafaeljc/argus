package io.github.rafaeljc.argus.common.application.ratelimit;

public interface RateLimiter {

    ConsumptionResult tryConsume(String bucketName, String key);
}
