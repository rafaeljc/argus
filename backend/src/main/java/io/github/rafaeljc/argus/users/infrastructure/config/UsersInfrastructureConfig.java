package io.github.rafaeljc.argus.users.infrastructure.config;

import io.github.rafaeljc.argus.users.application.EnsureSoleAdmin;
import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import io.github.rafaeljc.argus.users.infrastructure.Argon2IdPasswordEncoder;
import io.github.rafaeljc.argus.users.infrastructure.bootstrap.SoleAdminInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(AdminAssignmentProperties.class)
public class UsersInfrastructureConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2IdPasswordEncoder();
    }

    @Bean
    @Profile({"local", "prod"})
    public SoleAdminInitializer soleAdminInitializer(AdminAssignmentProperties properties,
                                                     EnsureSoleAdmin ensureSoleAdmin) {
        return new SoleAdminInitializer(properties, ensureSoleAdmin);
    }
}
