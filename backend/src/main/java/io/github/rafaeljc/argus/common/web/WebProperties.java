package io.github.rafaeljc.argus.common.web;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Cross-origin browser posture for a deployment that splits the SPA and the API across two
// hosts. Both knobs are optional: blank means same-origin (local profile, every *IT) and leaves
// CORS unregistered and the CSRF cookie host-only — see SecurityConfig and CsrfCookieFactory. A
// non-blank value must be well-formed at boot, not discovered later as a silent CORS/cookie
// mismatch in production.
@ConfigurationProperties("argus.web")
public record WebProperties(String corsAllowedOrigin, String cookieDomain) {

    public WebProperties {
        corsAllowedOrigin = corsAllowedOrigin == null ? "" : corsAllowedOrigin;
        cookieDomain = cookieDomain == null ? "" : cookieDomain;
        if (!corsAllowedOrigin.isBlank()) {
            validateOrigin(corsAllowedOrigin);
        }
        if (!cookieDomain.isBlank()) {
            validateCookieDomain(cookieDomain);
        }
    }

    private static void validateOrigin(String origin) {
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "argus.web.cors-allowed-origin is not a valid URI: " + origin, e);
        }
        String rawPath = uri.getRawPath();
        boolean hasPath = rawPath != null && !rawPath.isEmpty();
        if (uri.getScheme() == null || uri.getHost() == null
                || hasPath || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "argus.web.cors-allowed-origin must be a bare origin (scheme://host[:port]) "
                            + "with no path, query or fragment: " + origin);
        }
    }

    private static void validateCookieDomain(String domain) {
        if (domain.startsWith(".") || domain.indexOf(':') >= 0 || domain.indexOf('/') >= 0) {
            throw new IllegalArgumentException(
                    "argus.web.cookie-domain must be a bare hostname with no scheme, port or path: " + domain);
        }
    }
}
