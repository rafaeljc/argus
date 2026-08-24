package io.github.rafaeljc.argus.users.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Unvalidated on purpose, unlike AppProperties: a malformed value here must degrade to a log line
// rather than refuse to start, because the assignment already in the database is durable and
// outlives any single boot.
@ConfigurationProperties("argus.admin")
public record AdminAssignmentProperties(String userId) {}
