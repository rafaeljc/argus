package io.github.rafaeljc.argus.auth.infrastructure.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.infrastructure.security.SessionAuthenticationToken;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.SessionRequiredException;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.UserService;
import io.github.rafaeljc.argus.users.domain.AccountSuspendedException;
import io.github.rafaeljc.argus.users.domain.EmailNotVerifiedException;
import io.github.rafaeljc.argus.users.domain.User;
import jakarta.servlet.FilterChain;
import java.time.Instant;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class AccountStateGateFilterTest {

    private static final String PROTECTED_PATH = "/api/v1/transactions";
    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");

    @Mock
    private UserService userService;

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    private AccountStateGateFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AccountStateGateFilter(userService, exceptionResolver);
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(PROTECTED_PATH);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noAuthentication_passesThroughAndSkipsUserLookup() throws Exception {
        filter.doFilter(request, response, chain);

        verifyNoInteractions(userService);
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertChainProceeded();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/account/me",
            "/api/v1/auth/logout",
            "/api/v1/auth/status"})
    void doFilter_exemptPath_passesThroughAndSkipsUserLookup(String path) throws Exception {
        authenticate();
        request.setRequestURI(path);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(userService);
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertChainProceeded();
    }

    @Test
    void doFilter_deletedUser_delegatesSessionRequired() throws Exception {
        UserId userId = authenticate();
        when(userService.lookup(userId)).thenReturn(user(userId, true, false, true));

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(SessionRequiredException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_suspendedUser_delegatesAccountSuspended() throws Exception {
        UserId userId = authenticate();
        when(userService.lookup(userId)).thenReturn(user(userId, true, true, false));

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(AccountSuspendedException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_unverifiedUser_delegatesEmailNotVerified() throws Exception {
        UserId userId = authenticate();
        when(userService.lookup(userId)).thenReturn(user(userId, false, false, false));

        filter.doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                any(EmailNotVerifiedException.class));
        assertChainDidNotProceed();
    }

    @Test
    void doFilter_activeVerifiedUser_passesThrough() throws Exception {
        UserId userId = authenticate();
        when(userService.lookup(userId)).thenReturn(user(userId, true, false, false));

        filter.doFilter(request, response, chain);

        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
        assertChainProceeded();
    }

    private UserId authenticate() {
        UserId userId = new UserId(UuidCreator.getTimeOrderedEpoch());
        SessionId sessionId = new SessionId(UuidCreator.getTimeOrderedEpoch());
        SecurityContextHolder.getContext().setAuthentication(
                new SessionAuthenticationToken(userId, sessionId));
        return userId;
    }

    private static User user(UserId id, boolean verified, boolean suspended, boolean deleted) {
        return new User(
                id,
                "user@example.com",
                "$argon2id$placeholder",
                verified,
                suspended,
                deleted,
                false,
                NOW,
                NOW,
                deleted ? NOW : null);
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
