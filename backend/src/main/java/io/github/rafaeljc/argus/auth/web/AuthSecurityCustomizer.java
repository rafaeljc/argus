package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.auth.application.AuthService;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import io.github.rafaeljc.argus.common.web.RateLimitFilter;
import io.github.rafaeljc.argus.common.web.SecurityFilterChainCustomizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final AuthService authService;
    private final AccountStateGateFilter accountStateGateFilter;
    private final RateLimitFilter rateLimitFilter;
    private final HandlerExceptionResolver exceptionResolver;
    // LogoutConfigurer.logoutUrl() builds a raw-URI matcher, not the MVC-mapping-aware one
    // authorizeHttpRequests uses, so it needs the servlet path spelled out explicitly here.
    private final String logoutUrl;

    AuthSecurityCustomizer(AuthService authService,
                           AccountStateGateFilter accountStateGateFilter,
                           RateLimitFilter rateLimitFilter,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
                           @Value("${spring.mvc.servlet.path}") String servletPath) {
        this.authService = authService;
        this.accountStateGateFilter = accountStateGateFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.exceptionResolver = exceptionResolver;
        this.logoutUrl = servletPath + "/auth/logout";
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        // Order downstream of UsernamePasswordAuthenticationFilter — a stable anchor in Spring
        // Security's fixed filter order, present or not — which guarantees SecurityContextHolder
        // is already populated for this request by the time these filters run.
        // AccountStateGate -> RateLimit. Rate-limiting runs last so it sees the authenticated
        // principal (when present) for user-keyed buckets, but still ahead of Spring's
        // authorization filter so abusive traffic is throttled before the policy layer touches it.
        http.addFilterAfter(accountStateGateFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, AccountStateGateFilter.class)
                .logout(logout -> logout
                        .logoutUrl(logoutUrl)
                        .addLogoutHandler(this::auditLogout)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                // Anonymous requests to authenticated endpoints must surface as 401 UNAUTHORIZED
                // via the shared ApiErrorHandler envelope, not Spring Security's default 403.
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, ex) ->
                        exceptionResolver.resolveException(request, response, null,
                                new SessionRequiredException())));
    }

    private void auditLogout(HttpServletRequest request,
                             HttpServletResponse response,
                             Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            authService.logout(user.userId());
        }
    }
}
