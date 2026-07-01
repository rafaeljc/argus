package io.github.rafaeljc.argus.common.web;

import io.github.rafaeljc.argus.common.application.ratelimit.BucketSelection;
import io.github.rafaeljc.argus.common.application.ratelimit.ConsumptionResult;
import io.github.rafaeljc.argus.common.application.ratelimit.RateLimiter;
import io.github.rafaeljc.argus.common.domain.Clock;
import io.github.rafaeljc.argus.common.domain.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RETRY_AFTER = "Retry-After";

    private static final String METHOD_OPTIONS = "OPTIONS";

    private final RateLimiter rateLimiter;
    private final BucketResolver bucketResolver;
    private final HandlerExceptionResolver exceptionResolver;
    private final Clock clock;

    public RateLimitFilter(RateLimiter rateLimiter,
                           BucketResolver bucketResolver,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
                           Clock clock) {
        this.rateLimiter = rateLimiter;
        this.bucketResolver = bucketResolver;
        this.exceptionResolver = exceptionResolver;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // CORS preflights carry no auth and must not deplete buckets; they're answered by the
        // CORS handler before reaching business endpoints.
        if (METHOD_OPTIONS.equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        BucketSelection selection = bucketResolver.resolve(request, currentUserId());
        ConsumptionResult result = rateLimiter.tryConsume(selection.bucketName(), selection.key());

        writeRateLimitHeaders(response, result);

        if (!result.allowed()) {
            response.setHeader(HEADER_RETRY_AFTER, Long.toString(result.secondsUntilRefill()));
            exceptionResolver.resolveException(request, response, null,
                    new RateLimitExceededException(result.secondsUntilRefill()));
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeRateLimitHeaders(HttpServletResponse response, ConsumptionResult result) {
        long resetEpochSeconds = clock.now().getEpochSecond() + result.secondsUntilReset();
        response.setHeader(HEADER_LIMIT, Long.toString(result.limit()));
        response.setHeader(HEADER_REMAINING, Long.toString(result.remainingTokens()));
        response.setHeader(HEADER_RESET, Long.toString(resetEpochSeconds));
    }

    private static Optional<String> currentUserId() {
        // Authentication.getName() is the framework-level accessor for the principal's stable
        // identifier; SessionAuthenticationToken overrides it to return the user id. Reading
        // through this contract keeps the filter free of auth-module imports.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String name = auth.getName();
        return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(name);
    }
}
