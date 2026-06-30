package io.github.rafaeljc.argus.common.domain;

import java.util.Map;

public final class RateLimitExceededException extends DomainException {

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("rate limit exceeded");
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException(
                    "retryAfterSeconds must be >= 0, got: " + retryAfterSeconds);
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public String code() {
        return "RATE_LIMIT_EXCEEDED";
    }

    @Override
    public int status() {
        return 429;
    }

    @Override
    public Map<String, String> headers() {
        return Map.of(RETRY_AFTER_HEADER, Long.toString(retryAfterSeconds));
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
