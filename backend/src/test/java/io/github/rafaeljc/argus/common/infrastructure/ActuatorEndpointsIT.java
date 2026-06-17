package io.github.rafaeljc.argus.common.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@AutoConfigureMetrics
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ActuatorEndpointsIT {

    @Value("${local.management.port}")
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> get(String path) {
        String url = "http://localhost:" + managementPort + path;
        return restTemplate.getForEntity(url, String.class);
    }

    @Test
    void livenessProbe_returns200Up() {
        ResponseEntity<String> response = get("/actuator/health/liveness");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessProbe_returns200Up() {
        ResponseEntity<String> response = get("/actuator/health/readiness");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void infoEndpoint_exposesAppMetadata() {
        ResponseEntity<String> response = get("/actuator/info");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"app\"")
                .contains("\"name\":\"argus\"")
                .contains("\"version\"");
    }

    @Test
    void metricsEndpoint_listsMetricNames() {
        ResponseEntity<String> response = get("/actuator/metrics");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"names\"").contains("jvm.memory.used");
    }

    @Test
    void prometheusEndpoint_returnsTextFormatWithHelpAndType() {
        ResponseEntity<String> response = get("/actuator/prometheus");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/plain");
        assertThat(response.getBody()).contains("# HELP").contains("# TYPE");
    }
}
