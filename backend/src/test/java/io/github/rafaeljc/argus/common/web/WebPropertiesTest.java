package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebPropertiesTest {

    @Test
    void constructor_nullPair_normalizesToBlank() {
        WebProperties properties = new WebProperties(null, null);

        assertThat(properties.corsAllowedOrigin()).isEmpty();
        assertThat(properties.cookieDomain()).isEmpty();
    }

    @Test
    void constructor_blankPair_isAccepted() {
        WebProperties properties = new WebProperties("", "");

        assertThat(properties.corsAllowedOrigin()).isEmpty();
        assertThat(properties.cookieDomain()).isEmpty();
    }

    @Test
    void constructor_wellFormedOriginAndDomain_isAccepted() {
        WebProperties properties = new WebProperties("https://app.argus.example", "argus.example");

        assertThat(properties.corsAllowedOrigin()).isEqualTo("https://app.argus.example");
        assertThat(properties.cookieDomain()).isEqualTo("argus.example");
    }

    @Test
    void constructor_originWithTrailingSlash_throws() {
        assertThatThrownBy(() -> new WebProperties("https://app.argus.example/", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cors-allowed-origin");
    }

    @Test
    void constructor_originWithPath_throws() {
        assertThatThrownBy(() -> new WebProperties("https://app.argus.example/login", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cors-allowed-origin");
    }

    @Test
    void constructor_originWithQuery_throws() {
        assertThatThrownBy(() -> new WebProperties("https://app.argus.example?x=1", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cors-allowed-origin");
    }

    @Test
    void constructor_originMissingScheme_throws() {
        assertThatThrownBy(() -> new WebProperties("app.argus.example", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cors-allowed-origin");
    }

    @Test
    void constructor_cookieDomainWithScheme_throws() {
        assertThatThrownBy(() -> new WebProperties("", "https://argus.example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cookie-domain");
    }

    @Test
    void constructor_cookieDomainWithLeadingDot_throws() {
        assertThatThrownBy(() -> new WebProperties("", ".argus.example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cookie-domain");
    }

    @Test
    void constructor_cookieDomainWithPort_throws() {
        assertThatThrownBy(() -> new WebProperties("", "argus.example:8080"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cookie-domain");
    }

    @Test
    void constructor_cookieDomainWithPath_throws() {
        assertThatThrownBy(() -> new WebProperties("", "argus.example/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argus.web.cookie-domain");
    }
}
