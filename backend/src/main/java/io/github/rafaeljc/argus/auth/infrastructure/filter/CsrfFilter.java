package io.github.rafaeljc.argus.auth.infrastructure.filter;

import io.github.rafaeljc.argus.auth.infrastructure.security.CsrfCookieFactory;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class CsrfFilter extends OncePerRequestFilter {

    private static final String CSRF_HEADER = "X-CSRF-Token";

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    // Endpoints the SPA hits before it has a session (and therefore before the argus_csrf cookie
    // exists). Listed exactly as they appear on the wire; matched by exact equality on
    // request.getRequestURI() to avoid accidentally exempting deeper paths.
    private static final Set<String> PUBLIC_POST_ALLOWLIST = Set.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/password-reset-requests",
            "/api/v1/auth/password-resets");

    private final HandlerExceptionResolver exceptionResolver;

    public CsrfFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!STATE_CHANGING_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (PUBLIC_POST_ALLOWLIST.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        if (!isAuthenticated()) {
            exceptionResolver.resolveException(request, response, null, new SessionRequiredException());
            return;
        }

        if (!csrfTokenMatches(request)) {
            exceptionResolver.resolveException(request, response, null,
                    new AccessDeniedException("CSRF token missing or mismatched"));
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated();
    }

    private static boolean csrfTokenMatches(HttpServletRequest request) {
        String header = request.getHeader(CSRF_HEADER);
        Optional<String> cookie = readCsrfCookie(request);
        if (header == null || header.isBlank() || cookie.isEmpty() || cookie.get().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8),
                cookie.get().getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> readCsrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> CsrfCookieFactory.COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
