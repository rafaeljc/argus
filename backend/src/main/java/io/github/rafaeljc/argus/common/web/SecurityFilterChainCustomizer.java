package io.github.rafaeljc.argus.common.web;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@FunctionalInterface
public interface SecurityFilterChainCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
