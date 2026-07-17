package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.common.web.SessionCookies;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CsrfCookieFactory {

    public static final String COOKIE_NAME = SessionCookies.CSRF_COOKIE_NAME;
    public static final Duration ROLLING_WINDOW = Duration.ofDays(30);

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
        return cookie;
    }

    public Cookie cleared() {
        return SessionCookies.clearedCsrf();
    }
}
