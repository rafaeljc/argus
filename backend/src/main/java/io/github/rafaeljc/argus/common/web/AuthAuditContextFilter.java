package io.github.rafaeljc.argus.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthAuditContextFilter extends OncePerRequestFilter {

    public static final String MDC_IP_ADDRESS = "ip_address";
    public static final String MDC_USER_AGENT = "user_agent";
    public static final String UNKNOWN = "unknown";

    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final int USER_AGENT_MAX_CHARS = 512;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(MDC_IP_ADDRESS, resolveIpAddress(request));
        MDC.put(MDC_USER_AGENT, resolveUserAgent(request));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_IP_ADDRESS);
            MDC.remove(MDC_USER_AGENT);
        }
    }

    private static String resolveIpAddress(HttpServletRequest request) {
        String value = request.getRemoteAddr();
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private static String resolveUserAgent(HttpServletRequest request) {
        String value = request.getHeader(USER_AGENT_HEADER);
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.length() <= USER_AGENT_MAX_CHARS ? value : value.substring(0, USER_AGENT_MAX_CHARS);
    }
}
