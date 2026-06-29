package io.github.rafaeljc.argus.auth.infrastructure.security;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieFactory {

    public static final String COOKIE_NAME = "argus_session";
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
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(PATH);
        cookie.setAttribute("SameSite", SAME_SITE);
        return cookie;
    }
}
