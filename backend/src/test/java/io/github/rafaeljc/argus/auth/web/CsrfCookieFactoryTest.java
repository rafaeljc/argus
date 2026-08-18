package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.common.web.WebProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class CsrfCookieFactoryTest {

    private static final String RAW_TOKEN = "raw-csrf-token";

    @Test
    void forToken_blankCookieDomain_omitsDomain() {
        CsrfCookieFactory factory = new CsrfCookieFactory(new WebProperties("", ""));

        Cookie cookie = factory.forToken(RAW_TOKEN);

        assertThat(cookie.getName()).isEqualTo(CsrfCookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo(RAW_TOKEN);
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo((int) CsrfCookieFactory.ROLLING_WINDOW.toSeconds());
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void forToken_configuredCookieDomain_setsDomain() {
        CsrfCookieFactory factory = new CsrfCookieFactory(new WebProperties("", "argus.example"));

        Cookie cookie = factory.forToken(RAW_TOKEN);

        assertThat(cookie.getDomain()).isEqualTo("argus.example");
    }

    @Test
    void cleared_blankCookieDomain_omitsDomain() {
        CsrfCookieFactory factory = new CsrfCookieFactory(new WebProperties("", ""));

        Cookie cookie = factory.cleared();

        assertThat(cookie.getName()).isEqualTo(CsrfCookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void cleared_configuredCookieDomain_setsSameDomainAsForToken() {
        CsrfCookieFactory factory = new CsrfCookieFactory(new WebProperties("", "argus.example"));

        Cookie cookie = factory.cleared();

        assertThat(cookie.getDomain()).isEqualTo("argus.example");
    }
}
