package io.github.rafaeljc.argus.support.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.event.BeforeTestMethodEvent;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class RedisContainer {

    @Bean
    @ServiceConnection("redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
    }

    @Bean
    ApplicationListener<BeforeTestMethodEvent> flushRedisBeforeEachTest(RedisConnectionFactory connectionFactory) {
        return event -> {
            try (var connection = connectionFactory.getConnection()) {
                connection.serverCommands().flushAll();
            }
        };
    }
}
