package io.github.rafaeljc.argus.auth.infrastructure.filter;

import io.github.rafaeljc.argus.auth.infrastructure.security.AuthenticatedSession;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.EmailNotVerifiedException;
import io.github.rafaeljc.argus.users.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class AccountStateGateFilter extends OncePerRequestFilter {

    // Endpoints reachable by a session-bearing user regardless of verify/suspend status: the
    // user must still be able to inspect themselves and sign out without being trapped.
    private static final Set<String> EXEMPT_FROM_STATE_GATE = Set.of(
            "/api/v1/account/me",
            "/api/v1/auth/logout",
            "/api/v1/auth/status");

    private final UserService userService;
    private final HandlerExceptionResolver exceptionResolver;

    public AccountStateGateFilter(UserService userService,
                                  @Qualifier("handlerExceptionResolver")
                                  HandlerExceptionResolver exceptionResolver) {
        this.userService = userService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthenticatedSession principal = currentPrincipal();
        if (principal == null || EXEMPT_FROM_STATE_GATE.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        User user = userService.lookup(principal.userId());
        if (user.isDeleted()) {
            // Anti-enumeration: a soft-deleted user whose session row still exists is treated
            // exactly like a missing session — same 401 UNAUTHORIZED, no leak of deletion state.
            exceptionResolver.resolveException(request, response, null, new SessionRequiredException());
            return;
        }
        if (user.isSuspended()) {
            exceptionResolver.resolveException(request, response, null,
                    new AccountSuspendedException(user.id(), user.email()));
            return;
        }
        if (!user.isVerified()) {
            exceptionResolver.resolveException(request, response, null,
                    new EmailNotVerifiedException(user.id(), user.email()));
            return;
        }

        chain.doFilter(request, response);
    }

    private static AuthenticatedSession currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof AuthenticatedSession session ? session : null;
    }
}
