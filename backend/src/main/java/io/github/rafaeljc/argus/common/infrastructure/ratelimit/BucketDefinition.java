package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import java.time.Duration;

public record BucketDefinition(long capacity, long refillTokens, Duration refillDuration) {

    public BucketDefinition {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be > 0, got: " + refillTokens);
        }
        if (refillDuration == null || refillDuration.isZero() || refillDuration.isNegative()) {
            throw new IllegalArgumentException("refillDuration must be a positive duration, got: " + refillDuration);
        }
    }
}
