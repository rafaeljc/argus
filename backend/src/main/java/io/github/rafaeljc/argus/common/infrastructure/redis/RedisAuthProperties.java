package io.github.rafaeljc.argus.common.infrastructure.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("argus.redis")
public record RedisAuthProperties(String cacheName) {}
