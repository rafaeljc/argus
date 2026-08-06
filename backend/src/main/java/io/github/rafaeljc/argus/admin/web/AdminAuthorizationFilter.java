package io.github.rafaeljc.argus.admin.web;

import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class AdminAuthorizationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

    private final UserService userService;
    private final HandlerExceptionResolver exceptionResolver;

    public AdminAuthorizationFilter(UserService userService,
                                    @Qualifier("handlerExceptionResolver")
                                    HandlerExceptionResolver exceptionResolver) {
        this.userService = userService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UserId userId = currentUserId();
        if (userId == null || !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        User user = userService.lookup(userId);
        if (!user.isAdmin()) {
            exceptionResolver.resolveException(request, response, null,
                    new AccessDeniedException("admin access required"));
            return;
        }

        chain.doFilter(request, response);
    }

    private static UserId currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return new UserId(UUID.fromString(auth.getName()));
    }
}
