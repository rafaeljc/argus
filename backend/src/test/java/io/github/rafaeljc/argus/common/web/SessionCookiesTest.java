package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class SessionCookiesTest {

    @Test
    void clearedSession_neverCarriesDomain() {
        Cookie cleared = SessionCookies.clearedSession();

        assertThat(cleared.getName()).isEqualTo(SessionCookies.SESSION_COOKIE_NAME);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/");
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.getSecure()).isTrue();
        assertThat(cleared.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cleared.getDomain()).isNull();
    }

    @Test
    void clearedCsrf_blankDomain_omitsDomain() {
        Cookie cleared = SessionCookies.clearedCsrf("");

        assertThat(cleared.getName()).isEqualTo(SessionCookies.CSRF_COOKIE_NAME);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/");
        assertThat(cleared.isHttpOnly()).isFalse();
        assertThat(cleared.getSecure()).isTrue();
        assertThat(cleared.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cleared.getDomain()).isNull();
    }

    @Test
    void clearedCsrf_nonBlankDomain_setsDomain() {
        Cookie cleared = SessionCookies.clearedCsrf("argus.example");

        assertThat(cleared.getDomain()).isEqualTo("argus.example");
    }
}
