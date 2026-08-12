package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.core.functions.Either;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class RetryAfterIntervalFunctionTest {

    private final RetryAfterIntervalFunction function = new RetryAfterIntervalFunction();

    @Test
    void apply_retryAfterHeaderPresent_usesHeaderValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "5");
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", headers, null, null);

        Long waitMillis = function.apply(1, Either.left(ex));

        assertThat(waitMillis).isEqualTo(5_000L);
    }

    @Test
    void apply_noRetryAfterHeader_fallsBackToDefaultWait() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", HttpHeaders.EMPTY, null, null);

        Long waitMillis = function.apply(1, Either.left(ex));

        assertThat(waitMillis).isEqualTo(RetryAfterIntervalFunction.DEFAULT_WAIT.toMillis());
    }

    @Test
    void apply_retryAfterHeaderNotNumeric_fallsBackToDefaultWait() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT");
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", headers, null, null);

        Long waitMillis = function.apply(1, Either.left(ex));

        assertThat(waitMillis).isEqualTo(RetryAfterIntervalFunction.DEFAULT_WAIT.toMillis());
    }

    @Test
    void apply_notAnHttpClientErrorException_fallsBackToDefaultWait() {
        Long waitMillis = function.apply(1, Either.left(new IllegalStateException("unexpected")));

        assertThat(waitMillis).isEqualTo(RetryAfterIntervalFunction.DEFAULT_WAIT.toMillis());
    }
}
