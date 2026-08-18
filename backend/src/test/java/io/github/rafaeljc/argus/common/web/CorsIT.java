package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({PostgresContainer.class, CorsIT.PingEndpoint.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "argus.web.cors-allowed-origin=https://app.argus.example")
class CorsIT {

    private static final String ALLOWED_ORIGIN = "https://app.argus.example";
    private static final String FOREIGN_ORIGIN = "https://evil.example";
    private static final String STATE_CHANGING_PATH = "/api/v1/__test/state-changing";
    private static final String SAFE_PATH = "/api/v1/__test/safe";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Test
    void preflightStateChanging_allowedOrigin_returns200WithCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-csrf-token");

        ResponseEntity<String> response = preflight(STATE_CHANGING_PATH, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("x-csrf-token");
    }

    @Test
    void preflightStateChanging_foreignOrigin_noAllowOriginHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, FOREIGN_ORIGIN);
        headers.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");

        ResponseEntity<String> response = preflight(STATE_CHANGING_PATH, headers);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void postStateChanging_unauthenticatedAllowedOrigin_returns401WithCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);

        ResponseEntity<String> response = http.exchange(
                url(STATE_CHANGING_PATH), HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void getSafe_allowedOrigin_exposesRateLimitAndLocationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);

        ResponseEntity<String> response = http.exchange(
                url(SAFE_PATH), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains("Location", "Retry-After", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset");
    }

    private ResponseEntity<String> preflight(String path, HttpHeaders headers) {
        return http.exchange(url(path), HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @RestController
    static class PingEndpoint {

        @PostMapping("/__test/state-changing")
        String stateChanging() {
            return "ok";
        }

        @GetMapping("/__test/safe")
        String safe() {
            return "ok";
        }
    }
}
