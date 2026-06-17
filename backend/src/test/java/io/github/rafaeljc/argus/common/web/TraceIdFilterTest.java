package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private static final String TRACE_ID_KEY = "traceId";

    private final TraceIdFilter filter = new TraceIdFilter();

    @BeforeEach
    void removeTraceIdBefore() {
        MDC.remove(TRACE_ID_KEY);
    }

    @AfterEach
    void removeTraceIdAfter() {
        MDC.remove(TRACE_ID_KEY);
    }

    @Test
    void doFilter_populatesUuidTraceIdOnMdcDuringChain() throws ServletException, IOException {
        String[] seenTraceId = new String[1];
        FilterChain chain = (req, res) -> seenTraceId[0] = MDC.get(TRACE_ID_KEY);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(seenTraceId[0]).isNotNull();
        assertThat(UUID.fromString(seenTraceId[0])).isNotNull();
    }

    @Test
    void doFilter_clearsTraceIdAfterChainCompletes() throws ServletException, IOException {
        FilterChain noopChain = (req, res) -> {};

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), noopChain);

        assertThat(MDC.get(TRACE_ID_KEY)).isNull();
    }

    @Test
    void doFilter_clearsTraceIdEvenWhenChainThrows() {
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(TRACE_ID_KEY)).isNull();
    }
}
