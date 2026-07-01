package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import io.github.rafaeljc.argus.common.domain.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class CsrfFilterTest {

    private static final String CSRF_COOKIE = "argus_csrf";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_VALUE = "csrf-token-value-001";
    private static final String PROTECTED_PATH = "/api/v1/transactions";

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    private CsrfFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CsrfFilter(exceptionResolver);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_safeMethod_passesThroughWithoutChecks() throws Exception {
        request.setMethod("GET");
        request.setRequestURI(PROTECTED_PATH);

        filter.doFilter(request, response, chain);

        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertChainProceeded();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/password-reset-requests",
            "/api/v1/auth/password-resets"})
    void doFilter_stateChangingOnPublicAllowlist_passesThrough(String path) throws Exception {
        request.setMethod("POST");
        request.setRequestURI(path);

        filter.doFilter(request, response, chain);

        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertChainProceeded();
    }

    @Test
    void doFilter_stateChangingUnauthenticatedNotInAllowlist_delegatesSessionRequired() throws Exception {
        request.setMethod("POST");
        request.setRequestURI(PROTECTED_PATH);

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(SessionRequiredException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_authenticatedHeaderMatchesCookie_passesThrough() throws Exception {
        authenticate();
        request.setMethod("POST");
        request.setRequestURI(PROTECTED_PATH);
        request.setCookies(new Cookie(CSRF_COOKIE, CSRF_VALUE));
        request.addHeader(CSRF_HEADER, CSRF_VALUE);

        filter.doFilter(request, response, chain);

        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertChainProceeded();
    }

    @Test
    void doFilter_authenticatedMissingHeader_delegatesAccessDenied() throws Exception {
        authenticate();
        request.setMethod("POST");
        request.setRequestURI(PROTECTED_PATH);
        request.setCookies(new Cookie(CSRF_COOKIE, CSRF_VALUE));

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(AccessDeniedException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_authenticatedMissingCookie_delegatesAccessDenied() throws Exception {
        authenticate();
        request.setMethod("POST");
        request.setRequestURI(PROTECTED_PATH);
        request.addHeader(CSRF_HEADER, CSRF_VALUE);

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(AccessDeniedException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_authenticatedHeaderMismatchesCookie_delegatesAccessDenied() throws Exception {
        authenticate();
        request.setMethod("POST");
        request.setRequestURI(PROTECTED_PATH);
        request.setCookies(new Cookie(CSRF_COOKIE, CSRF_VALUE));
        request.addHeader(CSRF_HEADER, "different-value");

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(AccessDeniedException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_authenticatedBlankHeaderAndCookie_delegatesAccessDenied() throws Exception {
        authenticate();
        request.setMethod("POST");
        request.setRequestURI(PROTECTED_PATH);
        request.setCookies(new Cookie(CSRF_COOKIE, ""));
        request.addHeader(CSRF_HEADER, "");

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(AccessDeniedException.class));
        assertChainDidNotProceed();
    }

    private void authenticate() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        SessionId sessionId = new SessionId(UuidCreator.getTimeOrderedEpoch());
        SecurityContextHolder.getContext().setAuthentication(
                new SessionAuthenticationToken(userId, sessionId));
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
