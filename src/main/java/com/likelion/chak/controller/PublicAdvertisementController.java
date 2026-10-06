package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdvertisementEventRequest;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.dto.EventRecordedResponse;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.service.AdvertisementService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/public/ads")
@RequiredArgsConstructor
public class PublicAdvertisementController {

    private final AdvertisementService advertisementService;
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

    @GetMapping
    public ResponseEntity<List<AdvertisementResponse>> getActive(
            @RequestParam("placement") AdPlacement placement) {
        return ResponseEntity.ok(advertisementService.getActive(placement));
    }

    @PostMapping("/{advertisementId}/events")
    public ResponseEntity<EventRecordedResponse> recordEvent(
            @PathVariable("advertisementId") Long advertisementId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody AdvertisementEventRequest request) {
        UserAccount user = principal == null ? null : authService.getUser(principal.userId());
        GuestSession guestSession = user == null ? guestIdentityService.resolveOrCreate(guestKey) : null;
        GuestIdentity guest = guestSession == null ? null : guestSession.getGuestIdentity();
        EventRecordedResponse response = advertisementService.recordEvent(
                advertisementId, user, guest, request);

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
                .sameSite(guestCookieSameSite)
                .path("/")
                .maxAge(Duration.ofSeconds(guestCookieMaxAgeSeconds))
                .build();
    }
}
