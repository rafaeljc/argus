package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.common.web.SessionCookies;
import io.github.rafaeljc.argus.common.web.WebProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CsrfCookieFactory {

    public static final String COOKIE_NAME = SessionCookies.CSRF_COOKIE_NAME;
    public static final Duration ROLLING_WINDOW = Duration.ofDays(30);

    private final WebProperties webProperties;

    public CsrfCookieFactory(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    public Cookie forToken(String rawToken) {
        Cookie cookie = new Cookie(COOKIE_NAME, rawToken);
        // Intentionally NOT HttpOnly: the SPA must read this cookie to echo it back in the
        // X-CSRF-Token header. That's the double-submit pattern — the value is not a secret,
        // the attacker's inability to read a different origin's cookies is what closes the loop.
        cookie.setHttpOnly(false);
        cookie.setSecure(true);
        cookie.setPath(SessionCookies.COOKIE_PATH);
        cookie.setAttribute("SameSite", SessionCookies.SAME_SITE);
        cookie.setMaxAge((int) ROLLING_WINDOW.toSeconds());
        // Widened to the parent domain (when configured) so a SPA hosted on a sibling subdomain
        // can read the value and echo it back. Safe to widen only because the value is not a
        // secret — the HttpOnly session cookie is what an attacker actually needs.
        if (!webProperties.cookieDomain().isBlank()) {
            cookie.setDomain(webProperties.cookieDomain());
        }
        return cookie;
    }

    public Cookie cleared() {
        return SessionCookies.clearedCsrf(webProperties.cookieDomain());
    }
}
