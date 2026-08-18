package io.github.rafaeljc.argus.email.infrastructure.resend;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Only bound where the real gateway is registered, so a blank key fails the boot exactly where the
// key is required rather than silently degrading a running instance.
@ConfigurationProperties("argus.email.resend")
public record ResendProperties(String apiKey, String apiUrl, Duration connectTimeout, Duration readTimeout) {

    public ResendProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("argus.email.resend.api-key must not be blank");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("argus.email.resend.api-url must not be blank");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("argus.email.resend.connect-timeout must be > 0");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("argus.email.resend.read-timeout must be > 0");
        }
    }
}
