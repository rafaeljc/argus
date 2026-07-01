package io.github.rafaeljc.argus.auth.web;

import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import io.github.rafaeljc.argus.common.web.RateLimitFilter;
import io.github.rafaeljc.argus.common.web.SecurityFilterChainCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final SessionResolutionFilter sessionResolutionFilter;
    private final CsrfFilter csrfFilter;
    private final AccountStateGateFilter accountStateGateFilter;
    private final RateLimitFilter rateLimitFilter;
    private final HandlerExceptionResolver exceptionResolver;

    AuthSecurityCustomizer(SessionResolutionFilter sessionResolutionFilter,
                           CsrfFilter csrfFilter,
                           AccountStateGateFilter accountStateGateFilter,
                           RateLimitFilter rateLimitFilter,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.sessionResolutionFilter = sessionResolutionFilter;
        this.csrfFilter = csrfFilter;
        this.accountStateGateFilter = accountStateGateFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        // UsernamePasswordAuthenticationFilter is a stable anchor in Spring Security's chain.
        // Order downstream of it: SessionResolution -> Csrf -> AccountStateGate -> RateLimit.
        // Rate-limiting runs last so it sees the authenticated principal (when present) for
        // user-keyed buckets, but still ahead of Spring's authorization filter so abusive
        // traffic is throttled before the policy layer touches it.
        http.addFilterBefore(sessionResolutionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, SessionResolutionFilter.class)
                .addFilterAfter(accountStateGateFilter, CsrfFilter.class)
                .addFilterAfter(rateLimitFilter, AccountStateGateFilter.class)
                // Anonymous requests to authenticated endpoints must surface as 401 UNAUTHORIZED
                // via the shared ApiErrorHandler envelope, not Spring Security's default 403.
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, ex) ->
                        exceptionResolver.resolveException(request, response, null,
                                new SessionRequiredException())));
    }
}
