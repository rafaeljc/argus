package io.github.rafaeljc.argus.common.infrastructure.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.ClientResources;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails.Standalone;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RedisClientConfig {

    // A RedisClient the application owns outright, for callers -- Bucket4j -- that need to open
    // and hold their own connection. LettuceConnectionFactory's native client is unsuitable: it
    // is torn down and rebuilt across the factory's own Lifecycle restarts, which silently
    // invalidates any connection opened from it earlier. Built from the same connection details
    // and ClientResources bean Spring Session's factory uses, so host/port/TLS/auth match and
    // both share one netty event-loop group instead of starting a second.
    @Bean(destroyMethod = "shutdown")
    RedisClient redisClient(DataRedisConnectionDetails connectionDetails, DataRedisProperties properties,
            ClientResources clientResources, ObjectProvider<RedisCredentialsProvider> credentialsProvider) {
        Standalone standalone = connectionDetails.getStandalone();
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort())
                .withDatabase(standalone.getDatabase())
                .withTimeout(properties.getTimeout())
                .withSsl(connectionDetails.getSslBundle() != null);
        credentialsProvider.ifAvailable(uri::withAuthentication);
        return RedisClient.create(clientResources, uri.build());
    }
}
