package io.github.rafaeljc.argus.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionAuthenticationToken;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.RateLimitExceededException;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final String IP = "203.0.113.7";

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, new BucketResolver(), exceptionResolver, new FixedClock(NOW));
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/signup");
        request.setRemoteAddr(IP);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_belowLimit_setsHeadersAndProceeds() throws Exception {
        when(rateLimiter.tryConsume("RL.auth.signup", IP))
                .thenReturn(new ConsumptionResult(true, 5L, 4L, 0L, 600L));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo(String.valueOf(NOW.getEpochSecond() + 600L));
        assertThat(response.getHeader("Retry-After")).isNull();
        assertChainProceeded();
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    void doFilter_exhausted_setsHeadersAndDispatchesRateLimitExceeded() throws Exception {
        when(rateLimiter.tryConsume("RL.auth.signup", IP))
                .thenReturn(new ConsumptionResult(false, 5L, 0L, 30L, 600L));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo(String.valueOf(NOW.getEpochSecond() + 600L));
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(RateLimitExceededException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_authenticatedGet_usesUserIdKeyOnReadBucket() throws Exception {
        UserId userId = authenticate();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/account/me");
        when(rateLimiter.tryConsume("RL.read", userId.value().toString()))
                .thenReturn(new ConsumptionResult(true, 300L, 299L, 0L, 60L));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("300");
        assertChainProceeded();
    }

    @Test
    void doFilter_optionsPreflight_bypassesRateLimiter() throws Exception {
        request.setMethod("OPTIONS");
        request.setRequestURI("/api/v1/account/me");

        filter.doFilter(request, response, chain);

        verify(rateLimiter, never()).tryConsume(any(), any());
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        assertChainProceeded();
    }

    private UserId authenticate() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        SessionId sessionId = new SessionId(UuidCreator.getTimeOrderedEpoch());
        SecurityContextHolder.getContext().setAuthentication(
                new SessionAuthenticationToken(userId, sessionId));
        return userId;
    }

    private void assertChainProceeded() {
        MockFilterChain mockChain = (MockFilterChain) chain;
        assertThat(mockChain.getRequest()).isSameAs(request);
        assertThat(mockChain.getResponse()).isSameAs(response);
    }

    private void assertChainDidNotProceed() {
        MockFilterChain mockChain = (MockFilterChain) chain;
        assertThat(mockChain.getRequest()).isNull();
        assertThat(mockChain.getResponse()).isNull();
    }
}
