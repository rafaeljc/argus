package io.github.rafaeljc.argus.common.infrastructure.redis;

import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.lettuce.RedisCredentialsProviderFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
@EnableConfigurationProperties(RedisAuthProperties.class)
class RedisAuthConfig {

    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    ElastiCacheTokenFactory elastiCacheTokenFactory(
            AwsCredentialsProvider awsCredentialsProvider, RedisAuthProperties properties) {
        return new ElastiCacheTokenFactory(
                awsCredentialsProvider, new DefaultAwsRegionProviderChain().getRegion(), properties);
    }

    // A standalone bean, not just a LettuceClientConfigurationBuilderCustomizer detail, so
    // RedisClientConfig's hand-built RedisClient can authenticate the same way as the
    // Spring-managed connection factory below.
    @Bean
    RedisCredentialsProvider elastiCacheCredentialsProvider(
            ElastiCacheTokenFactory tokens, DataRedisConnectionDetails connectionDetails) {
        String username = connectionDetails.getUsername();
        return RedisCredentialsProvider.from(() -> RedisCredentials.just(username, tokens.newToken(username)));
    }

    // RedisCredentialsProviderFactory has no single abstract method (both createCredentialsProvider
    // and createSentinelCredentialsProvider carry default bodies), so it cannot be a lambda target.
    @Bean
    LettuceClientConfigurationBuilderCustomizer elastiCacheIamAuthCustomizer(
            RedisCredentialsProvider elastiCacheCredentialsProvider) {
        return builder -> builder.redisCredentialsProviderFactory(new RedisCredentialsProviderFactory() {
            @Override
            public RedisCredentialsProvider createCredentialsProvider(RedisConfiguration configuration) {
                return elastiCacheCredentialsProvider;
            }
        });
    }
}
