package io.github.rafaeljc.argus.users.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rafaeljc.argus.support.containers.PostgresContainer;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({PostgresContainer.class, TestCurrentUserIdConfig.class})
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerIT {

    private static final String ENDPOINT = "/api/v1/account/me";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserService userService;

    @Test
    void getMe_authenticatedUser_returns200WithSafeAccountEnvelope() throws Exception {
        User seeded = userService.createUnverified("alice@example.com", "correct horse battery staple");

        ResponseEntity<String> response = exchange(seeded);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");

        String body = response.getBody();

        // Schema columns that must never reach the wire (see V1 migration `users` table).
        assertThat(body)
                .doesNotContain("password_hash")
                .doesNotContain("is_suspended")
                .doesNotContain("is_deleted")
                .doesNotContain("updated_at")
                .doesNotContain("deleted_at");

        JsonNode root = json.readTree(body);
        assertThat(root.propertyNames()).containsExactly("data");

        JsonNode data = root.get("data");
        assertThat(data.propertyNames()).containsExactlyInAnyOrder(
                "id", "email", "is_verified", "is_admin", "created_at");
        assertThat(data.get("id").asString()).isEqualTo(seeded.id().value().toString());
        assertThat(data.get("email").asString()).isEqualTo(seeded.email());
        assertThat(data.get("is_verified").asBoolean()).isEqualTo(seeded.isVerified());
        assertThat(data.get("is_admin").asBoolean()).isEqualTo(seeded.isAdmin());
        assertThat(data.get("created_at").asString()).isEqualTo(seeded.createdAt().toString());
    }

    private ResponseEntity<String> exchange(User authenticatedAs) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TestCurrentUserIdConfig.HEADER, authenticatedAs.id().value().toString());
        return http.exchange(
                "http://localhost:" + port + ENDPOINT,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }
}
