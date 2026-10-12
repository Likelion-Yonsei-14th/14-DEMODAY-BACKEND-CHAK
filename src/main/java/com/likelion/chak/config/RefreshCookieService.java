package com.likelion.chak.config;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 리프레시 토큰을 HttpOnly 쿠키로 주고받는다.
 * 쿠키로 받은 토큰은 브라우저가 자동 전송하므로 Origin 헤더가 허용 목록에 있는지 확인한다.
 */
@Component
public class RefreshCookieService {

    private final String name;
    private final boolean secure;
    private final String sameSite;
    private final String path;
    private final long maxAgeSeconds;
    private final boolean includeInBody;
    private final List<String> allowedOrigins;

    public RefreshCookieService(
            @Value("${chak.refresh-cookie.name:chak_refresh_token}") String name,
            @Value("${chak.refresh-cookie.secure:false}") boolean secure,
            @Value("${chak.refresh-cookie.same-site:Lax}") String sameSite,
            @Value("${chak.refresh-cookie.path:/api/auth}") String path,
            @Value("${chak.refresh-cookie.include-in-body:true}") boolean includeInBody,
            @Value("${jwt.refresh-token-expiration-seconds}") long maxAgeSeconds,
            @Value("${chak.cors.allowed-origins}") String allowedOrigins) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
        this.path = path;
        this.includeInBody = includeInBody;
        this.maxAgeSeconds = maxAgeSeconds;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public boolean isIncludeInBody() {
        return includeInBody;
    }

    public String issueHeader(String refreshToken) {
        return build(refreshToken, Duration.ofSeconds(maxAgeSeconds)).toString();
    }

    public String clearHeader() {
        return build("", Duration.ZERO).toString();
    }

    /** 본문 토큰이 있으면 본문을, 없으면 쿠키를 사용한다. 쿠키를 쓰는 경우 Origin을 검증한다. */
    public String resolve(HttpServletRequest request, String bodyToken) {
        if (bodyToken != null && !bodyToken.isBlank()) {
            return bodyToken;
        }
        String cookieToken = readCookie(request);
        if (cookieToken == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return cookieToken;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
