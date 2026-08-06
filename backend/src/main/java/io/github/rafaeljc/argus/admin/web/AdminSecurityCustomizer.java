package io.github.rafaeljc.argus.admin.web;

import io.github.rafaeljc.argus.common.web.SecurityFilterChainCustomizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
class AdminSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final AdminAuthorizationFilter adminAuthorizationFilter;

    AdminSecurityCustomizer(AdminAuthorizationFilter adminAuthorizationFilter) {
        this.adminAuthorizationFilter = adminAuthorizationFilter;
    }

    @Override
    public void customize(HttpSecurity http) {
        // Anchored on the same standard filter SessionResolutionFilter anchors before, so
        // ordering here is independent of which SecurityFilterChainCustomizer bean runs first —
        // this filter is guaranteed to run after SecurityContext is populated, regardless.
        http.addFilterAfter(adminAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
