package io.github.rafaeljc.argus.email.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.email.application.port.EmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.noop.NoOpLoggingEmailGateway;
import io.github.rafaeljc.argus.email.infrastructure.resend.ResendEmailGateway;
import io.github.rafaeljc.argus.support.annotations.NoDatabase;
import io.github.resilience4j.retry.Retry;
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
class EmailVendorWiringIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Retry vendorEmailRetry;

    // The no-op is the default everywhere. Reaching a real vendor takes an explicit opt-in, so no
    // test run and no local run can send mail to a real inbox by accident.
    @Test
    void emailGateway_withoutVendorOptIn_isTheNoOpAdapter() {
        assertThat(applicationContext.getBeanNamesForType(EmailGateway.class)).hasSize(1);
        assertThat(applicationContext.getBean(EmailGateway.class))
                .isInstanceOf(NoOpLoggingEmailGateway.class)
                .isNotInstanceOf(ResendEmailGateway.class);
    }

    @Test
    void vendorEmailRetry_isConfiguredForThreeAttempts() {
        assertThat(vendorEmailRetry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void vendorEmailRetry_retriesTransientFailures() {
        Predicate<Throwable> shouldRetry = vendorEmailRetry.getRetryConfig().getExceptionPredicate();

        assertThat(shouldRetry.test(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))).isTrue();
        assertThat(shouldRetry.test(new ResourceAccessException("connection reset"))).isTrue();
        assertThat(shouldRetry.test(new IOException("broken pipe"))).isTrue();
    }

    // 4xx is about the message, not the vendor's health: a suppressed recipient or a rejected key
    // never becomes valid by trying again. 429 is absent too — the next poll tick is the retry.
    @Test
    void vendorEmailRetry_doesNotRetryClientErrorsOrRateLimiting() {
        Predicate<Throwable> shouldRetry = vendorEmailRetry.getRetryConfig().getExceptionPredicate();

        assertThat(shouldRetry.test(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))).isFalse();
        assertThat(shouldRetry.test(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_CONTENT))).isFalse();
        assertThat(shouldRetry.test(HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "rate limited", HttpHeaders.EMPTY, null, null)))
                .isFalse();
    }
}
