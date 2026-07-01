package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.domain.Session;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieFactory {

    public static final String COOKIE_NAME = "argus_session";

    private static final String SAME_SITE = "Lax";
    private static final String PATH = "/";

    public Cookie forToken(String rawToken) {
        Cookie cookie = baseCookie(rawToken);
        cookie.setMaxAge((int) Session.ROLLING_WINDOW.toSeconds());
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
