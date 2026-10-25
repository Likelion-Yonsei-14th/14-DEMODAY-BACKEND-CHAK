package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.EventRecordedResponse;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.TrafficEventRequest;
import com.likelion.chak.service.AnalyticsService;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.GuestIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/public/analytics")
@RequiredArgsConstructor
public class PublicAnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthService authService;
    private final GuestIdentityService guestIdentityService;

    @Value("${chak.guest-cookie.name}")
    private String guestCookieName;

    @Value("${chak.guest-cookie.max-age-seconds}")
    private long guestCookieMaxAgeSeconds;

    @Value("${chak.guest-cookie.secure}")
    private boolean guestCookieSecure;

    @Value("${chak.guest-cookie.same-site:Lax}")
    private String guestCookieSameSite;

    @PostMapping("/events")
    public ResponseEntity<EventRecordedResponse> record(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @RequestHeader(name = "X-Chak-Geo-Country", required = false) String chakCountry,
            @RequestHeader(name = "X-Chak-Geo-Region", required = false) String chakRegion,
            @RequestHeader(name = "X-Vercel-IP-Country", required = false) String vercelCountry,
            @RequestHeader(name = "X-Vercel-IP-Country-Region", required = false) String vercelRegion,
            @Valid @RequestBody TrafficEventRequest request,
            HttpServletRequest http) {
        UserAccount user = principal == null ? null : authService.getUser(principal.userId());
        GuestSession guestSession = user == null ? guestIdentityService.resolveOrCreateLimited(guestKey, ClientIp.of(http)) : null;
        GuestIdentity guest = guestSession == null ? null : guestSession.getGuestIdentity();
        AnalyticsService.Geo geo = AnalyticsService.Geo.fromHeaders(
                firstNonBlank(chakCountry, vercelCountry), firstNonBlank(chakRegion, vercelRegion));
        if (geo == null) {
            // 헤더가 CloudFront를 지나며 사라지는 경우를 위해, 프론트가 본문에 실어 보낸 값을 대신 쓴다.
            geo = AnalyticsService.Geo.fromHeaders(request.geoCountry(), request.geoRegion());
        }
        EventRecordedResponse response = analyticsService.record(user, guest, request, geo);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (guestSession != null && guestSession.isCreated()) {
            builder.header(HttpHeaders.SET_COOKIE, createGuestCookie(guest.getGuestKey()).toString());
        }
        return builder.body(response);
    }

    /** 프론트 호스팅의 미들웨어가 붙인 값을 우선하고, 호스팅이 직접 붙인 값을 대신 쓴다. */
    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private ResponseCookie createGuestCookie(String guestKey) {
        return ResponseCookie.from(guestCookieName, guestKey)
                .httpOnly(true)
                .secure(guestCookieSecure)
                .sameSite(guestCookieSameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(guestCookieMaxAgeSeconds))
                .build();
    }
}
