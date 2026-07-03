package io.github.rafaeljc.argus.common.web;

import static io.github.rafaeljc.argus.common.web.AuthAuditContextFilter.MDC_IP_ADDRESS;
import static io.github.rafaeljc.argus.common.web.AuthAuditContextFilter.MDC_USER_AGENT;
import static io.github.rafaeljc.argus.common.web.AuthAuditContextFilter.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthAuditContextFilterTest {

    private final AuthAuditContextFilter filter = new AuthAuditContextFilter();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.remove(MDC_IP_ADDRESS);
        MDC.remove(MDC_USER_AGENT);
    }

    @Test
    void doFilter_populatesIpAndUserAgentOnMdcDuringChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "TestAgent/1.0");

        String[] seen = new String[2];
        FilterChain chain = (req, res) -> {
            seen[0] = MDC.get(MDC_IP_ADDRESS);
            seen[1] = MDC.get(MDC_USER_AGENT);
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen[0]).isEqualTo("203.0.113.7");
        assertThat(seen[1]).isEqualTo("TestAgent/1.0");
    }

    @Test
    void doFilter_missingUserAgent_defaultsToUnknown() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        String[] seen = new String[1];
        FilterChain chain = (req, res) -> seen[0] = MDC.get(MDC_USER_AGENT);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen[0]).isEqualTo(UNKNOWN);
    }

    @Test
    void doFilter_blankRemoteAddr_defaultsToUnknown() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");

        String[] seen = new String[1];
        FilterChain chain = (req, res) -> seen[0] = MDC.get(MDC_IP_ADDRESS);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen[0]).isEqualTo(UNKNOWN);
    }

    @Test
    void doFilter_userAgentExceedsLimit_isTruncatedTo512Chars() throws ServletException, IOException {
        String oversized = "a".repeat(600);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", oversized);

        String[] seen = new String[1];
        FilterChain chain = (req, res) -> seen[0] = MDC.get(MDC_USER_AGENT);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seen[0]).hasSize(512).isEqualTo("a".repeat(512));
    }

    @Test
    void doFilter_clearsMdcAfterChainCompletes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "TestAgent/1.0");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(MDC.get(MDC_IP_ADDRESS)).isNull();
        assertThat(MDC.get(MDC_USER_AGENT)).isNull();
    }

    @Test
    void doFilter_clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "TestAgent/1.0");

        FilterChain failingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(MDC_IP_ADDRESS)).isNull();
        assertThat(MDC.get(MDC_USER_AGENT)).isNull();
    }
}
