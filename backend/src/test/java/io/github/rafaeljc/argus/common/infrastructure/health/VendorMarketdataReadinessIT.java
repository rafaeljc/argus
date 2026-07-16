package io.github.rafaeljc.argus.common.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

@Import(PostgresContainer.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class VendorMarketdataReadinessIT {

    @Autowired
    private CircuitBreaker vendorMarketdataBreaker;

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${local.management.port}")
    private int managementPort;

    @BeforeEach
    void resetBreaker() {
        vendorMarketdataBreaker.reset();
    }

    private ResponseEntity<String> getReadiness() {
        return restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health/readiness", String.class);
    }

    @Test
    void readiness_isUp_whenBreakerClosed() {
        ResponseEntity<String> response = getReadiness();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void readiness_isDown_whenBreakerForcedOpen() {
        vendorMarketdataBreaker.transitionToForcedOpenState();

        ResponseEntity<String> response = getReadiness();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
    }
}
