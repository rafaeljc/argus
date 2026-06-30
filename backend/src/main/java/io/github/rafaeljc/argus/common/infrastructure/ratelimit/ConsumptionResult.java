package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

public record ConsumptionResult(
        boolean allowed,
        long limit,
        long remainingTokens,
        long secondsUntilRefill,
        long secondsUntilReset) {

    public ConsumptionResult {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0, got: " + limit);
        }
        if (remainingTokens < 0) {
            throw new IllegalArgumentException("remainingTokens must be >= 0, got: " + remainingTokens);
        }
        if (secondsUntilRefill < 0) {
            throw new IllegalArgumentException("secondsUntilRefill must be >= 0, got: " + secondsUntilRefill);
        }
        if (secondsUntilReset < 0) {
            throw new IllegalArgumentException("secondsUntilReset must be >= 0, got: " + secondsUntilReset);
        }
    }
}
