package io.github.rafaeljc.argus.auth.infrastructure;

import io.github.rafaeljc.argus.auth.infrastructure.filter.SessionResolutionFilter;
import io.github.rafaeljc.argus.common.infrastructure.SecurityFilterChainCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final SessionResolutionFilter sessionResolutionFilter;

    AuthSecurityCustomizer(SessionResolutionFilter sessionResolutionFilter) {
        this.sessionResolutionFilter = sessionResolutionFilter;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        // UsernamePasswordAuthenticationFilter is a stable anchor in Spring Security's chain.
        // PR 4.3 will re-anchor against CsrfFilter so session resolution precedes CSRF per spec §5.2.
        http.addFilterBefore(sessionResolutionFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
