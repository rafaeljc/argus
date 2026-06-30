package io.github.rafaeljc.argus.auth.infrastructure;

import io.github.rafaeljc.argus.auth.infrastructure.filter.AccountStateGateFilter;
import io.github.rafaeljc.argus.auth.infrastructure.filter.CsrfFilter;
import io.github.rafaeljc.argus.auth.infrastructure.filter.SessionResolutionFilter;
import io.github.rafaeljc.argus.common.infrastructure.SecurityFilterChainCustomizer;
import io.github.rafaeljc.argus.common.infrastructure.ratelimit.RateLimitFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
class AuthSecurityCustomizer implements SecurityFilterChainCustomizer {

    private final SessionResolutionFilter sessionResolutionFilter;
    private final CsrfFilter csrfFilter;
    private final AccountStateGateFilter accountStateGateFilter;
    private final RateLimitFilter rateLimitFilter;

    AuthSecurityCustomizer(SessionResolutionFilter sessionResolutionFilter,
                           CsrfFilter csrfFilter,
                           AccountStateGateFilter accountStateGateFilter,
                           RateLimitFilter rateLimitFilter) {
        this.sessionResolutionFilter = sessionResolutionFilter;
        this.csrfFilter = csrfFilter;
        this.accountStateGateFilter = accountStateGateFilter;
        this.rateLimitFilter = rateLimitFilter;
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
                .addFilterAfter(rateLimitFilter, AccountStateGateFilter.class);
    }
}
