package io.github.rafaeljc.argus.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.f4b6a3.uuid.UuidCreator;
import io.github.rafaeljc.argus.auth.application.port.SessionRepository;
import io.github.rafaeljc.argus.auth.domain.Session;
import io.github.rafaeljc.argus.common.domain.FixedClock;
import io.github.rafaeljc.argus.common.domain.SessionId;
import io.github.rafaeljc.argus.common.domain.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SessionResolutionFilterTest {

    private static final String COOKIE_NAME = "argus_session";
    private static final String RAW_TOKEN = "raw-session-token-value";
    // SHA-256("raw-session-token-value") — pinned so the test is self-contained.
    private static final String EXPECTED_HASH =
            "05b58273d68f9a039d1797782dd4efe7139105cba098b81ef9336f58f061ac27";
    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final Duration ROLLING_WINDOW = Duration.ofDays(30);

    @Mock
    private SessionRepository sessionRepository;

    private SessionResolutionFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SessionResolutionFilter(sessionRepository, new SessionCookieFactory(), new FixedClock(NOW));
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "JUnit/5");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noCookie_doesNotTouchRepositoryAndLeavesContextEmpty() throws Exception {
        filter.doFilter(request, response, chain);

        verifyNoInteractions(sessionRepository);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getCookie(COOKIE_NAME)).isNull();
    }

    @Test
    void doFilter_unknownTokenHash_leavesContextEmptyAndDoesNotRefresh() throws Exception {
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(sessionRepository).findByTokenHash(EXPECTED_HASH);
        verify(sessionRepository, never()).touch(any(), any(), any(), any(), any());
        verify(sessionRepository, never()).deleteById(any());
        assertThat(response.getCookie(COOKIE_NAME)).isNull();
    }

    @Test
    void doFilter_expiredSession_deletesAndClearsCookieAndLeavesContextEmpty() throws Exception {
        UserId userId = userId();
        SessionId sessionId = sessionId();
        Instant created = NOW.minus(Duration.ofDays(40));
        Instant expired = NOW.minusSeconds(1);
        Session expiredSession = new Session(sessionId, userId, EXPECTED_HASH,
                "10.0.0.1", "old", created, expired, created);
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.of(expiredSession));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(sessionRepository).deleteById(sessionId);
        verify(sessionRepository, never()).touch(any(), any(), any(), any(), any());
        Cookie cleared = response.getCookie(COOKIE_NAME);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/");
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.getSecure()).isTrue();
        assertThat(cleared.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void doFilter_validSession_populatesContextAndTouchesAndReemitsCookie() throws Exception {
        UserId userId = userId();
        SessionId sessionId = sessionId();
        Instant created = NOW.minus(Duration.ofDays(2));
        Instant expires = NOW.plus(Duration.ofDays(28));
        Session validSession = new Session(sessionId, userId, EXPECTED_HASH,
                "10.0.0.1", "old", created, expires, created);
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.of(validSession));
        boolean[] sawAuthenticationDuringChain = {false};
        chain = (req, res) -> {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            sawAuthenticationDuringChain[0] = current instanceof SessionAuthenticationToken;
        };

        filter.doFilter(request, response, chain);

        assertThat(sawAuthenticationDuringChain[0]).isTrue();
        verify(sessionRepository).touch(
                eq(sessionId),
                eq(NOW),
                eq(NOW.plus(ROLLING_WINDOW)),
                eq("203.0.113.7"),
                eq("JUnit/5"));
        Cookie reemitted = response.getCookie(COOKIE_NAME);
        assertThat(reemitted).isNotNull();
        assertThat(reemitted.getValue()).isEqualTo(RAW_TOKEN);
        assertThat(reemitted.getMaxAge()).isEqualTo((int) ROLLING_WINDOW.toSeconds());
        assertThat(reemitted.getPath()).isEqualTo("/");
        assertThat(reemitted.isHttpOnly()).isTrue();
        assertThat(reemitted.getSecure()).isTrue();
        assertThat(reemitted.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void doFilter_validSession_clearsSecurityContextAfterChain() throws Exception {
        UserId userId = userId();
        SessionId sessionId = sessionId();
        Session validSession = new Session(sessionId, userId, EXPECTED_HASH, "10.0.0.1", "old",
                NOW.minus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(28)), NOW.minus(Duration.ofDays(2)));
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.of(validSession));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_validSession_truncatesUserAgentTo512Chars() throws Exception {
        UserId userId = userId();
        SessionId sessionId = sessionId();
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        request.removeHeader("User-Agent");
        request.addHeader("User-Agent", "x".repeat(1000));
        Session validSession = new Session(sessionId, userId, EXPECTED_HASH, "10.0.0.1", "old",
                NOW.minus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(28)), NOW.minus(Duration.ofDays(2)));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.of(validSession));

        filter.doFilter(request, response, chain);

        verify(sessionRepository).touch(
                eq(sessionId),
                eq(NOW),
                eq(NOW.plus(ROLLING_WINDOW)),
                eq("203.0.113.7"),
                eq("x".repeat(512)));
    }

    @Test
    void doFilter_validSession_nullUserAgentHeader_passesThroughAsNull() throws Exception {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        UserId userId = userId();
        SessionId sessionId = sessionId();
        Session validSession = new Session(sessionId, userId, EXPECTED_HASH, "10.0.0.1", "old",
                NOW.minus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(28)), NOW.minus(Duration.ofDays(2)));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.of(validSession));

        filter.doFilter(request, response, chain);

        verify(sessionRepository).touch(
                eq(sessionId),
                eq(NOW),
                eq(NOW.plus(ROLLING_WINDOW)),
                eq("203.0.113.7"),
                eq(null));
    }

    @Test
    void doFilter_validSession_principalCarriesUserIdAndSessionId() throws Exception {
        UserId userId = userId();
        SessionId sessionId = sessionId();
        request.setCookies(new Cookie(COOKIE_NAME, RAW_TOKEN));
        Session validSession = new Session(sessionId, userId, EXPECTED_HASH, "10.0.0.1", "old",
                NOW.minus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(28)), NOW.minus(Duration.ofDays(2)));
        when(sessionRepository.findByTokenHash(EXPECTED_HASH)).thenReturn(Optional.of(validSession));
        AuthenticatedSession[] observed = {null};
        chain = (req, res) -> observed[0] = (AuthenticatedSession)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        filter.doFilter(request, response, chain);

        assertThat(observed[0].userId()).isEqualTo(userId);
        assertThat(observed[0].sessionId()).isEqualTo(sessionId);
    }

    private static UserId userId() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    private static SessionId sessionId() {
        return new SessionId(UuidCreator.getTimeOrderedEpoch());
    }
}
