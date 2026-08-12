package io.github.rafaeljc.argus.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.infrastructure.massive.MassivePriceGateway;
import io.github.rafaeljc.argus.marketdata.infrastructure.noop.NoOpLoggingVendorPriceGateway;
import io.github.rafaeljc.argus.support.annotations.NoDatabase;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@NoDatabase
@SpringBootTest
class MarketdataVendorWiringIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Retry vendorMarketdataRetry;

    // The no-op is the default everywhere. Only an explicitly `prod` deployment talks to a real
    // vendor, so no test run and no local run can spend the account's quota by accident.
    @Test
    void vendorPriceGateway_outsideProdProfile_isTheNoOpAdapter() {
        assertThat(applicationContext.getBeanNamesForType(VendorPriceGateway.class)).hasSize(1);
        assertThat(applicationContext.getBean(VendorPriceGateway.class))
                .isInstanceOf(NoOpLoggingVendorPriceGateway.class)
                .isNotInstanceOf(MassivePriceGateway.class);
    }

    @Test
    void vendorMarketdataRetry_isConfiguredForThreeAttempts() {
        assertThat(vendorMarketdataRetry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    // A fixed short ladder would spend both retries inside the same vendor rate-limit window.
    @Test
    void vendorMarketdataRetry_backsOffExponentially() {
        long longestFirstWait = 0;
        long shortestLastWait = Long.MAX_VALUE;
        for (int sample = 0; sample < 100; sample++) {
            longestFirstWait = Math.max(longestFirstWait, waitAfterAttempt(1));
            shortestLastWait = Math.min(shortestLastWait, waitAfterAttempt(3));
        }

        assertThat(shortestLastWait).isGreaterThan(longestFirstWait);
    }

    @Test
    void vendorMarketdataRetry_retriesTransientFailures() {
        Predicate<Throwable> shouldRetry = vendorMarketdataRetry.getRetryConfig().getExceptionPredicate();

        assertThat(shouldRetry.test(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))).isTrue();
        assertThat(shouldRetry.test(new ResourceAccessException("connection reset"))).isTrue();
        assertThat(shouldRetry.test(new IOException("broken pipe"))).isTrue();
        assertThat(shouldRetry.test(
                        HttpClientErrorException.create(
                                HttpStatus.TOO_MANY_REQUESTS, "rate limited", HttpHeaders.EMPTY, null, null)))
                .isTrue();
    }

    // Retrying a rejected key or a missing resource only delays the failure and burns quota.
    @Test
    void vendorMarketdataRetry_doesNotRetryTerminalClientErrors() {
        Predicate<Throwable> shouldRetry = vendorMarketdataRetry.getRetryConfig().getExceptionPredicate();

        assertThat(shouldRetry.test(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))).isFalse();
        assertThat(shouldRetry.test(new HttpClientErrorException(HttpStatus.FORBIDDEN))).isFalse();
        assertThat(shouldRetry.test(new HttpClientErrorException(HttpStatus.NOT_FOUND))).isFalse();
    }

    private long waitAfterAttempt(int attempt) {
        RetryConfig config = vendorMarketdataRetry.getRetryConfig();
        return config.getIntervalBiFunction().apply(attempt, Either.right(null));
    }
}
