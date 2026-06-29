package io.github.rafaeljc.argus.common.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHeadersIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders headers;

    @BeforeEach
    void fetchResponseHeaders() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/api/v1/", String.class);
        headers = response.getHeaders();
    }

    @Test
    void response_includesStrictTransportSecurityWithOneYearAndPreload() {
        String hsts = headers.getFirst("Strict-Transport-Security");

        assertThat(hsts)
                .isNotNull()
                .contains("max-age=31536000")
                .contains("includeSubDomains")
                .contains("preload");
    }

    @Test
    void response_includesXContentTypeOptionsNosniff() {
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void response_includesXFrameOptionsDeny() {
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void response_includesReferrerPolicyStrictOriginWhenCrossOrigin() {
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void response_includesPermissionsPolicyMinimalAllowlist() {
        assertThat(headers.getFirst("Permissions-Policy"))
                .isEqualTo("camera=(), microphone=(), geolocation=()");
    }
}
