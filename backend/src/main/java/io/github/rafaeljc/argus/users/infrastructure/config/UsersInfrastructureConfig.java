package io.github.rafaeljc.argus.users.infrastructure.config;

import io.github.rafaeljc.argus.users.application.port.PasswordEncoder;
import io.github.rafaeljc.argus.users.infrastructure.Argon2IdPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsersInfrastructureConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2IdPasswordEncoder();
    }
}
