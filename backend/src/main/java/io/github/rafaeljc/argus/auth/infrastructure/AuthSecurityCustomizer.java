package io.github.rafaeljc.argus.auth.infrastructure;

import io.github.rafaeljc.argus.auth.infrastructure.filter.CsrfFilter;
import io.github.rafaeljc.argus.auth.infrastructure.filter.SessionResolutionFilter;
import io.github.rafaeljc.argus.common.infrastructure.SecurityFilterChainCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final SessionResolutionFilter sessionResolutionFilter;
    private final CsrfFilter csrfFilter;

    AuthSecurityCustomizer(SessionResolutionFilter sessionResolutionFilter,
                           CsrfFilter csrfFilter) {
        this.sessionResolutionFilter = sessionResolutionFilter;
        this.csrfFilter = csrfFilter;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        // UsernamePasswordAuthenticationFilter is a stable anchor in Spring Security's chain.
        // Order downstream of it: SessionResolution -> Csrf.
        http.addFilterBefore(sessionResolutionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, SessionResolutionFilter.class);
    }
}
