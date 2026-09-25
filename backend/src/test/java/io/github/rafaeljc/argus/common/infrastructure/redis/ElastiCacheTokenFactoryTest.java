package io.github.rafaeljc.argus.common.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class ElastiCacheTokenFactoryTest {

    private static final String CACHE_NAME = "argus-prod-cache";
    private static final String USER_ID = "argus-prod-backend";

    private final ElastiCacheTokenFactory tokenFactory = new ElastiCacheTokenFactory(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key-id", "test-secret-key")),
            Region.US_EAST_1,
            new RedisAuthProperties(CACHE_NAME));

    @Test
    void newToken_signsConnectRequest_withNoUriScheme() {
        String token = tokenFactory.newToken(USER_ID);

        assertThat(token).doesNotStartWith("https://").doesNotStartWith("http://");
    }

    @Test
    void newToken_signsConnectRequest_hostIsTheCacheNameAndTargetsTheUser() {
        String token = tokenFactory.newToken(USER_ID);

        assertThat(hostOf(token)).isEqualTo(CACHE_NAME);
        assertThat(queryParams(token)).containsEntry("Action", "connect").containsEntry("User", USER_ID);
    }

    @Test
    void newToken_signsConnectRequest_carriesSigV4QueryParameters() {
        Map<String, String> query = queryParams(tokenFactory.newToken(USER_ID));

        assertThat(query).containsEntry("X-Amz-Algorithm", "AWS4-HMAC-SHA256")
                .containsEntry("X-Amz-Expires", String.valueOf(ElastiCacheTokenFactory.TOKEN_TTL.toSeconds()));
        assertThat(query.get("X-Amz-Credential")).contains("/elasticache/aws4_request");
        assertThat(query.get("X-Amz-Signature")).isNotBlank();
    }

    private static String hostOf(String token) {
        return URI.create("https://" + token).getHost();
    }

    private static Map<String, String> queryParams(String token) {
        String query = URI.create("https://" + token).getRawQuery();
        return Arrays.stream(query.split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> decode(pair[0]), pair -> decode(pair[1])));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
