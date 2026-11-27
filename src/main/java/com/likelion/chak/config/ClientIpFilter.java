package com.likelion.chak.config;

import com.likelion.chak.controller.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 원본 X-Forwarded-For로 접속자 IP를 한 번 계산해 요청 속성에 넣는다. ForwardedHeaderFilter보다 먼저 돌아야 한다
 * (그 필터는 X-Forwarded-For를 지우고 remoteAddr를 가장 왼쪽 값, 즉 위조 가능한 값으로 바꾼다).
 * 믿는 칸 수와 비밀 헤더의 뜻은 {@link ClientIp} 설명을 본다.
 */
public class ClientIpFilter extends OncePerRequestFilter {

    public static final String EDGE_SECRET_HEADER = "X-Chak-Edge-Secret";

    private final int trustedHops;
    private final byte[] edgeSecret;

    public ClientIpFilter(int trustedHops, String edgeSecret) {
        this.trustedHops = Math.max(1, trustedHops);
        this.edgeSecret = edgeSecret == null || edgeSecret.isBlank()
                ? null : edgeSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(ClientIp.ATTRIBUTE,
                ClientIp.resolve(request.getHeader("X-Forwarded-For"), request.getRemoteAddr(), hopsFor(request)));
        chain.doFilter(request, response);
    }

    int hopsFor(HttpServletRequest request) {
        if (edgeSecret == null) {
            return trustedHops;
        }
        String presented = request.getHeader(EDGE_SECRET_HEADER);
        boolean viaEdge = presented != null
                && MessageDigest.isEqual(edgeSecret, presented.getBytes(StandardCharsets.UTF_8));
        // 비밀값이 없으면 앞단 프록시(Vercel)를 거치지 않은 요청이다. 그 프록시가 붙였다는 칸은 호출자가 쓴 것이므로 믿지 않는다.
        return viaEdge ? trustedHops : Math.max(1, trustedHops - 1);
    }
}
