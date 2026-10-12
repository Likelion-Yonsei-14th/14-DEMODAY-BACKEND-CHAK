package com.likelion.chak.service;

import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.dto.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 비회원 쿠키 발급을 한곳에서 처리한다(새 컨트롤러용). */
@Service
@RequiredArgsConstructor
public class GuestCookieService {

    private final GuestIdentityService guestIdentityService;

    @Value("${chak.guest-cookie.name}")
    private String cookieName;

    @Value("${chak.guest-cookie.max-age-seconds}")
    private long maxAgeSeconds;

    @Value("${chak.guest-cookie.secure}")
    private boolean secure;

    @Value("${chak.guest-cookie.same-site:Lax}")
    private String sameSite;

    public GuestSession resolveOrCreate(String guestKey) {
        return guestIdentityService.resolveOrCreate(guestKey);
    }

    /** 새로 만든 비회원이면 Set-Cookie 헤더 값을, 아니면 null을 반환한다. */
    public String cookieHeaderIfCreated(GuestSession session) {
        if (session == null || !session.isCreated()) {
            return null;
        }
        GuestIdentity guest = session.getGuestIdentity();
        return ResponseCookie.from(cookieName, guest.getGuestKey())
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build()
                .toString();
    }

    public static void addHeader(org.springframework.http.ResponseEntity.BodyBuilder builder, String header) {
        if (header != null) {
            builder.header(HttpHeaders.SET_COOKIE, header);
        }
    }
}
