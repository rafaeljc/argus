package io.github.rafaeljc.argus.common.web;

import jakarta.servlet.http.Cookie;

// Wire-contract constants and cleared-cookie factories for the two authentication cookies. Kept
// in common.web so both auth.web (issuing cookies on login / logout) and other modules whose
// endpoints terminate a session (e.g. account deletion) reach the same names and attributes
// without any cross-module import.
public final class SessionCookies {

    public static final String SESSION_COOKIE_NAME = "argus_session";
    public static final String CSRF_COOKIE_NAME = "argus_csrf";
    public static final String COOKIE_PATH = "/";
    public static final String SAME_SITE = "Lax";

    private SessionCookies() {
    }

    public static Cookie clearedSession() {
        return cleared(SESSION_COOKIE_NAME, true);
    }

    // The CSRF cookie is intentionally not HttpOnly at issuance so the SPA can echo it in the
    // X-CSRF-Token header (double-submit pattern). The cleared variant preserves the same
    // attribute set so browsers overwrite the original rather than co-existing with it.
    public static Cookie clearedCsrf() {
        return cleared(CSRF_COOKIE_NAME, false);
    }

    private static Cookie cleared(String name, boolean httpOnly) {
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setAttribute("SameSite", SAME_SITE);
        return cookie;
    }
}
