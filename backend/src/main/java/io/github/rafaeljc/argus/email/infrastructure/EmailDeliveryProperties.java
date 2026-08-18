package io.github.rafaeljc.argus.email.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The address mail comes from. Only bound where a real gateway is registered, so a blank value
// fails the boot exactly where it is required rather than shipping mail with no sender.
@ConfigurationProperties("argus.email")
public record EmailDeliveryProperties(String address) {

    public EmailDeliveryProperties {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("argus.email.address must not be blank");
        }
    }
}
