package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.web.SessionCookies;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieFactory {

    public static final String COOKIE_NAME = SessionCookies.SESSION_COOKIE_NAME;

    public Cookie forToken(String rawToken) {
        Cookie cookie = new Cookie(COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(SessionCookies.COOKIE_PATH);
        cookie.setAttribute("SameSite", SessionCookies.SAME_SITE);
        cookie.setMaxAge((int) Session.ROLLING_WINDOW.toSeconds());
        return cookie;
    }

    public Cookie cleared() {
        return SessionCookies.clearedSession();
    }
}
