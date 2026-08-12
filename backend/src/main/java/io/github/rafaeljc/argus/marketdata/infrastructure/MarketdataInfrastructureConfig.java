package io.github.rafaeljc.argus.marketdata.infrastructure;

import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.marketdata.application.BackfillWorker;
import io.github.rafaeljc.argus.marketdata.application.port.VendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.infrastructure.massive.MassivePriceGateway;
import io.github.rafaeljc.argus.marketdata.infrastructure.massive.MassiveProperties;
import io.github.rafaeljc.argus.marketdata.infrastructure.massive.MassiveResponseMapper;
import io.github.rafaeljc.argus.marketdata.infrastructure.noop.NoOpLoggingVendorPriceGateway;
import io.github.rafaeljc.argus.marketdata.infrastructure.scheduler.BackfillScheduler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
public class MarketdataInfrastructureConfig {

    // Fails fast at startup if the named instance is missing from application.yaml,
    // which is preferable to a NullPointerException on the first vendor call.
    @Bean
    public CircuitBreaker vendorMarketdataBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("vendor-marketdata");
    }

    // The breaker protects Argus from a sick vendor; it does nothing about the ordinary transient
    // failure — a dropped connection, a 502 from a proxy, a 429 from the rate limiter. Those are
    // retried with exponential backoff inside the adapter before the breaker ever sees them.
    @Bean
    public Retry vendorMarketdataRetry(RetryRegistry registry) {
        return registry.retry("vendor-marketdata");
    }

    // The two gateway beans below are mutually exclusive by construction, so exactly one exists in
    // every environment. The no-op is the default: only an explicitly `prod` deployment reaches a
    // real vendor.
    @Bean
    @Profile("!prod")
    public VendorPriceGateway noOpVendorPriceGateway() {
        return new NoOpLoggingVendorPriceGateway();
    }

    @Bean
    @Profile({"local", "prod"})
    public BackfillScheduler backfillScheduler(BackfillWorker worker) {
        return new BackfillScheduler(worker);
    }

    // Nested so MassiveProperties is bound only under `prod`. Bound unconditionally, a deployment
    // with no vendor credentials — every local run and every test — would fail to start.
    @Configuration(proxyBeanMethods = false)
    @Profile("prod")
    @EnableConfigurationProperties(MassiveProperties.class)
    static class VendorMarketdataConfig {

        @Bean
        MassiveResponseMapper massiveResponseMapper() {
            return new MassiveResponseMapper();
        }

        @Bean
        VendorPriceGateway massivePriceGateway(
                RestClient.Builder builder,
                MassiveProperties properties,
                MassiveResponseMapper mapper,
                Retry vendorMarketdataRetry,
                Clock clock) {
            // Timeouts belong on the transport, not in the adapter: without them a stalled vendor
            // connection pins the backfill worker or the EOD step indefinitely.
            RestClient.Builder configured = builder.requestFactory(ClientHttpRequestFactoryBuilder.detect()
                    .build(HttpClientSettings.defaults()
                            .withTimeouts(properties.connectTimeout(), properties.readTimeout())));
            return new MassivePriceGateway(configured, properties, mapper, vendorMarketdataRetry, clock);
        }
    }
}
