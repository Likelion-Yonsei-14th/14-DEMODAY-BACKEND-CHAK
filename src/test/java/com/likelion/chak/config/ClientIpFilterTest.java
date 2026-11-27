package com.likelion.chak.config;

import com.likelion.chak.controller.ClientIp;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpFilterTest {

    private static String resolve(ClientIpFilter filter, String forwardedFor, String edgeSecret) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/chat");
        request.setRemoteAddr("130.176.0.1");   // CloudFront 엣지
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        if (edgeSecret != null) request.addHeader(ClientIpFilter.EDGE_SECRET_HEADER, edgeSecret);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return ClientIp.of(request);
    }

    @Test
    void doesNotTrustTheLeftmostValueACallerCanWrite() throws Exception {
        var filter = new ClientIpFilter(2, "");
        // Vercel 경유: "<접속자>, <Vercel>" → 접속자
        assertThat(resolve(filter, "198.51.100.7, 76.76.21.21", null)).isEqualTo("198.51.100.7");
        // 호출자가 앞에 아무 값이나 붙여도 오른쪽 두 칸만 본다
        assertThat(resolve(filter, "1.1.1.1, 2.2.2.2, 198.51.100.7, 76.76.21.21", null)).isEqualTo("198.51.100.7");
        // 믿는 칸의 값이 주소가 아니면 더 오른쪽(프록시가 붙인) 값으로 물러선다
        assertThat(resolve(filter, "garbage, 76.76.21.21", null)).isEqualTo("76.76.21.21");
        assertThat(resolve(filter, null, null)).isEqualTo("130.176.0.1");
    }

    @Test
    void withAnEdgeSecretDirectCallersAreKeyedByTheAddressCloudFrontAppended() throws Exception {
        var filter = new ClientIpFilter(2, "s3cret-value");
        // 비밀값을 가진 앞단(Vercel)을 거친 요청은 두 칸을 믿는다
        assertThat(resolve(filter, "198.51.100.7, 76.76.21.21", "s3cret-value")).isEqualTo("198.51.100.7");
        // CloudFront를 직접 부른 요청: 왼쪽 값은 호출자가 쓴 것이라 무시하고, CloudFront가 붙인 실제 주소를 쓴다
        assertThat(resolve(filter, "9.9.9.9, 203.0.113.50", null)).isEqualTo("203.0.113.50");
        assertThat(resolve(filter, "9.9.9.9, 203.0.113.50", "wrong")).isEqualTo("203.0.113.50");
    }
}
