package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("argus.rate-limit")
public record RateLimitProperties(Map<String, BucketDefinition> buckets) {

    public RateLimitProperties {
        buckets = buckets == null ? Map.of() : Map.copyOf(buckets);
    }
}
