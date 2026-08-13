package io.github.rafaeljc.argus.email.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Vendor-independent delivery settings: the address mail comes from, and the host the links inside
// it point at. Only bound where a real gateway is registered, so a blank value fails the boot
// exactly where it is required rather than shipping mail with dead links in it.
@ConfigurationProperties("argus.email")
public record EmailDeliveryProperties(String fromAddress, String appBaseUrl) {

    public EmailDeliveryProperties {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("argus.email.from-address must not be blank");
        }
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            throw new IllegalArgumentException("argus.email.app-base-url must not be blank");
        }
    }
}
