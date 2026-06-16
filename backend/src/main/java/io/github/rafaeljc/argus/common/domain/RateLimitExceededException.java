package io.github.rafaeljc.argus.common.domain;

public final class RateLimitExceededException extends DomainException {

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

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
