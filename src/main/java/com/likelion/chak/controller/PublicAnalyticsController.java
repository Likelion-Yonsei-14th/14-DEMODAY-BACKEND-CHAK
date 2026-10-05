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

    @PostMapping("/events")
    public ResponseEntity<EventRecordedResponse> record(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody TrafficEventRequest request) {
        UserAccount user = principal == null ? null : authService.getUser(principal.userId());
        GuestSession guestSession = user == null ? guestIdentityService.resolveOrCreate(guestKey) : null;
        GuestIdentity guest = guestSession == null ? null : guestSession.getGuestIdentity();
        EventRecordedResponse response = analyticsService.record(user, guest, request);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (guestSession != null && guestSession.isCreated()) {
            builder.header(HttpHeaders.SET_COOKIE, createGuestCookie(guest.getGuestKey()).toString());
        }
        return builder.body(response);
    }

    private ResponseCookie createGuestCookie(String guestKey) {
        return ResponseCookie.from(guestCookieName, guestKey)
                .httpOnly(true)
                .secure(guestCookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(guestCookieMaxAgeSeconds))
                .build();
    }
}
