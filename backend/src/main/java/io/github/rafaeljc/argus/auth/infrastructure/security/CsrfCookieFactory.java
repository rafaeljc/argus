package io.github.rafaeljc.argus.auth.infrastructure.security;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CsrfCookieFactory {

    public static final String COOKIE_NAME = "argus_csrf";
    public static final Duration ROLLING_WINDOW = Duration.ofDays(30);

    private static final String SAME_SITE = "Lax";
    private static final String PATH = "/";

    public Cookie forToken(String rawToken) {
        Cookie cookie = baseCookie(rawToken);
        cookie.setMaxAge((int) ROLLING_WINDOW.toSeconds());
        return cookie;
    }

    public Cookie cleared() {
        Cookie cookie = baseCookie("");
        cookie.setMaxAge(0);
        return cookie;
    }

    private static Cookie baseCookie(String value) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        // Intentionally NOT HttpOnly: the SPA must read this cookie to echo it back in the
        // X-CSRF-Token header. That's the double-submit pattern — the value is not a secret,
        // the attacker's inability to read a different origin's cookies is what closes the loop.
        cookie.setHttpOnly(false);
        cookie.setSecure(true);
        cookie.setPath(PATH);
        cookie.setAttribute("SameSite", SAME_SITE);
        return cookie;
    }
}
