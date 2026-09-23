package io.github.rafaeljc.argus.common.web;

// Wire-contract constants for the CSRF cookie. Kept in common.web so both SecurityConfig (issuing
// the cookie via CookieCsrfTokenRepository) and the OpenAPI-documented contract agree on one name.
public final class SessionCookies {

    public static final String CSRF_COOKIE_NAME = "argus_csrf";
    public static final String COOKIE_PATH = "/";
    public static final String SAME_SITE = "Lax";

    private SessionCookies() {
    }
}
