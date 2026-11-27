package com.likelion.chak.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(1_000, 5_000);

    @Test
    void rejectsADeclaredOversizedBodyBeforeAnythingReadsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/analytics/events");
        request.setContent(new byte[1_001]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void messageCreationGetsTheLargerLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/desks/abc-token/messages");
        request.setContent(new byte[4_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void stopsReadingAChunkedBodyOnceItPassesTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/chat") {
            @Override
            public long getContentLengthLong() {
                return -1;   // Transfer-Encoding: chunked
            }
        };
        request.setContent(new byte[2_000]);
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set((HttpServletRequest) req));
        assertThatThrownBy(() -> seen.get().getInputStream().readAllBytes())
                .isInstanceOf(IOException.class)
                .isInstanceOf(RequestBodyLimitFilter.PayloadTooLargeException.class);
    }
}
