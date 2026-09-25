package io.github.rafaeljc.argus.common.infrastructure.redis;

import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner.AuthLocation;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.regions.Region;

public final class ElastiCacheTokenFactory {

    static final Duration TOKEN_TTL = Duration.ofSeconds(900);

    private static final String SERVICE_SIGNING_NAME = "elasticache";
    private static final AwsV4HttpSigner SIGNER = AwsV4HttpSigner.create();

    private final AwsCredentialsProvider credentialsProvider;
    private final Region region;
    private final String cacheName;

    public ElastiCacheTokenFactory(AwsCredentialsProvider credentialsProvider, Region region,
            RedisAuthProperties properties) {
        this.credentialsProvider = credentialsProvider;
        this.region = region;
        this.cacheName = properties.cacheName();
    }

    public String newToken(String userId) {
        SdkHttpRequest request = SdkHttpRequest.builder()
                .method(SdkHttpMethod.GET)
                .protocol("https")
                .host(cacheName)
                .encodedPath("/")
                .putRawQueryParameter("Action", "connect")
                .putRawQueryParameter("User", userId)
                .build();

        SignedRequest signed = SIGNER.sign(r -> r
                .identity(credentialsProvider.resolveCredentials())
                .request(request)
                .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SERVICE_SIGNING_NAME)
                .putProperty(AwsV4HttpSigner.REGION_NAME, region.id())
                .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AuthLocation.QUERY_STRING)
                .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, TOKEN_TTL));

        return signed.request().getUri().toString().substring("https://".length());
    }
}
