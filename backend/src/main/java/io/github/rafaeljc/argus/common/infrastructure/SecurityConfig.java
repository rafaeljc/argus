package io.github.rafaeljc.argus.common.infrastructure;

import java.util.List;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration
class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;
    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    // Pre-session POST endpoints — the SPA hits these before the session cookie exists.
    // Listed unprefixed because spring.mvc.servlet.path strips /api/v1 before authz matching.
    private static final String[] PUBLIC_AUTH_POSTS = {
            "/auth/signup",
            "/auth/login",
            "/auth/verify-email",
            "/auth/password-reset-requests",
            "/auth/password-resets"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<SecurityFilterChainCustomizer> customizers)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Management endpoints live on a separate port; once a SecurityFilterChain
                        // bean is defined, Boot's ManagementWebSecurityAutoConfiguration backs off
                        // and these requests fall through to this chain. EndpointRequest matches
                        // on the actuator endpoint registry rather than the raw URI, which avoids
                        // pitfalls around base-path / servlet-path resolution on the mgmt port.
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POSTS).permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        // LB terminates TLS upstream, so the app sees HTTP. Override Spring
                        // Security's HTTPS-only default so the header reaches the browser, which
                        // receives the response over TLS from the LB.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                                .includeSubDomains(true)
                                .preload(true)
                                .requestMatcher(AnyRequestMatcher.INSTANCE))
                        .contentTypeOptions(c -> {})
                        .frameOptions(FrameOptionsConfig::deny)
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy(PERMISSIONS_POLICY)));
        for (SecurityFilterChainCustomizer customizer : customizers) {
            customizer.customize(http);
        }
        return http.build();
    }
}
