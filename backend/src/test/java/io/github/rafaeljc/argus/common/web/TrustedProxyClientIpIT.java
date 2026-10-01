package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.auth.TestLogin;
import io.github.rafaeljc.argus.support.auth.TestSession;
import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.support.containers.RedisContainer;
import io.github.rafaeljc.argus.support.web.ClientIpEchoController;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.application.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.ResponseEntity;

// No forward-headers-strategy override here: server.forward-headers-strategy: native is the
// base application.yaml default, active in every profile including this one, so this exercises
// the same RemoteIpValve wiring every environment actually runs. The test client plays the real
// TCP peer the valve sees; it connects over loopback, which the valve's default internalProxies
// regex already trusts, so no extra server.tomcat.remoteip.* configuration is needed.
@Import({PostgresContainer.class, RedisContainer.class, ClientIpEchoController.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrustedProxyClientIpIT {

    private static final String ENDPOINT = "/api/v1/test/client-ip";

    // Simulates CloudFront appending the real viewer IP and the ALB appending its own VPC
    // peer after whatever the client sent. 10.0.2.17 falls inside the valve's default
    // trusted 10.0.0.0/8 range, so it's skipped rather than mistaken for the client.
    private static final String REAL_VIEWER_IP = "203.0.113.9";
    private static final String VPC_ORIGIN_HOP = "10.0.2.17";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private TestLogin testLogin;

    @BeforeEach
    void setUp() {
        testLogin = new TestLogin(userService, userRepository, http, port);
    }

    @Test
    void clientIp_forgedLeftmostEntry_resolvesToRealViewerIp() {
        TestSession session = testLogin.login("trusted-proxy-it@example.com");

        String resolved = clientIp(session, "1.2.3.4", REAL_VIEWER_IP, VPC_ORIGIN_HOP);

        assertThat(resolved).isEqualTo(REAL_VIEWER_IP);
    }

    @Test
    void clientIp_differentForgedLeftmostEntry_stillResolvesToSameRealViewerIp() {
        TestSession session = testLogin.login("trusted-proxy-it-2@example.com");

        String first = clientIp(session, "1.2.3.4", REAL_VIEWER_IP, VPC_ORIGIN_HOP);
        String second = clientIp(session, "9.9.9.9", REAL_VIEWER_IP, VPC_ORIGIN_HOP);

        assertThat(first).isEqualTo(REAL_VIEWER_IP);
        assertThat(second).isEqualTo(REAL_VIEWER_IP);
    }

    private String clientIp(TestSession session, String... forwardedFor) {
        HttpHeaders headers = session.headers();
        headers.set("X-Forwarded-For", String.join(", ", forwardedFor));
        ResponseEntity<String> response = http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        return response.getBody();
    }
}
