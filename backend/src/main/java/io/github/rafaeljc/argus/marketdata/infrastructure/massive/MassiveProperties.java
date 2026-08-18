package io.github.rafaeljc.argus.marketdata.infrastructure.massive;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Only bound where the real gateway is registered, so a blank key fails the boot exactly where the
// key is required rather than silently degrading a running instance.
@ConfigurationProperties("argus.marketdata.massive")
public record MassiveProperties(String apiKey,
                                String apiUrl,
                                Duration connectTimeout,
                                Duration readTimeout,
                                int maxUniversePages) {

    public MassiveProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("argus.marketdata.massive.api-key must not be blank");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("argus.marketdata.massive.api-url must not be blank");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("argus.marketdata.massive.connect-timeout must be > 0");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("argus.marketdata.massive.read-timeout must be > 0");
        }
        if (maxUniversePages < 1) {
            throw new IllegalArgumentException(
                    "argus.marketdata.massive.max-universe-pages must be >= 1, got: " + maxUniversePages);
        }
    }
}
