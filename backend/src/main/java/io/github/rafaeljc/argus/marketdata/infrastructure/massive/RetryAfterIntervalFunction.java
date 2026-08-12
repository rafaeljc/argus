package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

// The vendor rate-limits the whole account, not a single endpoint, so a 429 is throttling, not a
// failure: waiting it out must not eat the transient-error retry budget (see vendor-marketdata's
// retryExceptions in application.yaml, which no longer includes TooManyRequests). This function
// backs the separate vendor-marketdata-throttle Retry instance and honors the vendor's Retry-After
// header when present, falling back to the documented per-minute window otherwise.
public final class RetryAfterIntervalFunction implements IntervalBiFunction<Object> {

    static final Duration DEFAULT_WAIT = Duration.ofSeconds(60);

    @Override
    public Long apply(Integer attempt, Either<Throwable, Object> outcome) {
        if (outcome.isRight() || !(outcome.getLeft() instanceof HttpClientErrorException ex)) {
            return DEFAULT_WAIT.toMillis();
        }
        String header =
                ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null) {
            return DEFAULT_WAIT.toMillis();
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim())).toMillis();
        } catch (NumberFormatException ex2) {
            return DEFAULT_WAIT.toMillis();
        }
    }
}
