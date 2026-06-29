package io.github.rafaeljc.argus.auth.infrastructure.filter;

import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionAuthenticationToken;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionCookieFactory;
import io.github.rafaeljc.argus.common.domain.Clock;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionResolutionFilter extends OncePerRequestFilter {

    private static final int USER_AGENT_MAX_CHARS = 512;
    private static final String USER_AGENT_HEADER = "User-Agent";

    private final SessionRepository sessionRepository;
    private final SessionCookieFactory cookieFactory;
    private final Clock clock;

    public SessionResolutionFilter(SessionRepository sessionRepository,
                                   SessionCookieFactory cookieFactory,
                                   Clock clock) {
        this.sessionRepository = sessionRepository;
        this.cookieFactory = cookieFactory;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = readSessionCookie(request);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<Session> found = sessionRepository.findByTokenHash(sha256Hex(token.get()));
        if (found.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Session session = found.get();
        Instant now = clock.now();
        if (!session.expiresAt().isAfter(now)) {
            sessionRepository.deleteById(session.id());
            response.addCookie(cookieFactory.cleared());
            chain.doFilter(request, response);
            return;
        }

        refreshSession(session, request, now);
        response.addCookie(cookieFactory.forToken(token.get()));
        SecurityContextHolder.getContext().setAuthentication(
                new SessionAuthenticationToken(session.userId(), session.id()));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void refreshSession(Session session, HttpServletRequest request, Instant now) {
        Instant newExpiresAt = now.plus(SessionCookieFactory.ROLLING_WINDOW);
        String ip = request.getRemoteAddr();
        String userAgent = truncate(request.getHeader(USER_AGENT_HEADER), USER_AGENT_MAX_CHARS);
        sessionRepository.touch(session.id(), now, newExpiresAt, ip, userAgent);
    }

    private static Optional<String> readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> SessionCookieFactory.COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE — unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
