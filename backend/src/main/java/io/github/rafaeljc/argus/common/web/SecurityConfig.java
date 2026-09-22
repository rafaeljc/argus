package io.github.rafaeljc.argus.common.web;

import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableConfigurationProperties(WebProperties.class)
class SecurityConfig {

    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;
    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";
    private static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private static final List<String> CORS_ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> CORS_ALLOWED_HEADERS =
            List.of("Content-Type", "Accept", CSRF_HEADER_NAME);
    // The response headers the OpenAPI contract promises and the SPA reads client-side (e.g. the
    // 429 Retry-After toast) — invisible to cross-origin JS unless explicitly exposed.
    private static final List<String> CORS_EXPOSED_HEADERS = List.of(
            "Location", "Retry-After", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset");
    private static final Duration CORS_MAX_AGE = Duration.ofHours(1);

    // Pre-session POST endpoints — the SPA hits these before the session cookie exists.
    // Listed unprefixed because spring.mvc.servlet.path strips /api/v1 before authz matching.
    private static final String[] PUBLIC_AUTH_POSTS = {
            "/auth/signup",
            "/auth/login",
            "/auth/verify-email",
            "/auth/password-reset-requests",
            "/auth/password-resets"};

    private static final String ADMIN_PATH_PATTERN = "/admin/**";
    private static final String ADMIN_ROLE = "ADMIN";

    @Bean
    CorsConfigurationSource corsConfigurationSource(WebProperties webProperties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Blank means a same-origin deployment (local profile, every *IT): no mapping is
        // registered, so Spring Security's CorsFilter becomes a pass-through.
        if (webProperties.corsAllowedOrigin().isBlank()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(webProperties.corsAllowedOrigin()));
        configuration.setAllowedMethods(CORS_ALLOWED_METHODS);
        configuration.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        configuration.setExposedHeaders(CORS_EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE);
        // Registered under "/**" rather than the API's own paths: this filter runs ahead of the
        // DispatcherServlet, so the lookup path here does not carry the spring.mvc.servlet.path
        // prefix the rest of this class matches against.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(WebProperties webProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(SessionCookies.CSRF_COOKIE_NAME);
        repository.setCookiePath(SessionCookies.COOKIE_PATH);
        repository.setHeaderName(CSRF_HEADER_NAME);
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(true).sameSite(SessionCookies.SAME_SITE);
            if (!webProperties.cookieDomain().isBlank()) {
                cookie.domain(webProperties.cookieDomain());
            }
        });
        return repository;
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        // CsrfFilter runs ahead of AnonymousAuthenticationFilter in Spring Security's fixed filter
        // order, so a CSRF failure (or an authorizeHttpRequests denial) on a truly anonymous
        // request reaches here with no Authentication in the context yet — not, as
        // ExceptionTranslationFilter's own routing would assume, an AnonymousAuthenticationToken.
        // Treat null the same as anonymous: 401 UNAUTHORIZED via the shared envelope. Only a
        // denial against a real, authenticated principal is 403 FORBIDDEN.
        AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
        return (request, response, ex) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || trustResolver.isAnonymous(authentication)) {
                exceptionResolver.resolveException(request, response, null, new SessionRequiredException());
            } else {
                exceptionResolver.resolveException(request, response, null, ex);
            }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<SecurityFilterChainCustomizer> customizers,
                                            CorsConfigurationSource corsConfigurationSource,
                                            SecurityContextRepository securityContextRepository,
                                            CookieCsrfTokenRepository csrfTokenRepository,
                                            AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(PUBLIC_AUTH_POSTS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        // Management endpoints live on a separate port; once a SecurityFilterChain
                        // bean is defined, Boot's ManagementWebSecurityAutoConfiguration backs off
                        // and these requests fall through to this chain. EndpointRequest matches
                        // on the actuator endpoint registry rather than the raw URI, which avoids
                        // pitfalls around base-path / servlet-path resolution on the mgmt port.
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POSTS).permitAll()
                        .requestMatchers(ADMIN_PATH_PATTERN).hasRole(ADMIN_ROLE)
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh.accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
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
