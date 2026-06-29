package io.github.rafaeljc.argus.common.infrastructure;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration
class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;
    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<SecurityFilterChainCustomizer> customizers)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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
