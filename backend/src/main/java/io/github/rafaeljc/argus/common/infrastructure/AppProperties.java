package io.github.rafaeljc.argus.common.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The public host the product lives at. No default anywhere: a deployment without it would mail
// dead links to real users.
@ConfigurationProperties("argus")
public record AppProperties(String appBaseUrl) {

    public AppProperties {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            throw new IllegalArgumentException("argus.app-base-url must not be blank");
        }
    }
}
