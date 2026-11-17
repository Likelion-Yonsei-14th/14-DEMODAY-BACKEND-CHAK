package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdvertisementEventRequest;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.dto.EventRecordedResponse;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.service.AdCreativeService;
import com.likelion.chak.service.AdDeliveryService;
import com.likelion.chak.service.AdRegions;
import com.likelion.chak.service.AdvertisementService;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.GuestIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final AdCreativeService adCreativeService;
    private final AdDeliveryService adDeliveryService;
    private final com.likelion.chak.repository.GuestIdentityRepository guestIdentityRepository;
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

    /**
     * 이 사람에게 보여 줄 광고 하나. 고르는 기준은 {@link AdDeliveryService}. 없으면 204.
     * 위치는 호스팅이 붙인 헤더를 먼저 쓰고, 없으면 프론트가 보낸 값을 쓴다. 손님은 새로 만들지 않는다.
     */
    @GetMapping("/serve")
    public ResponseEntity<AdvertisementResponse> serve(
            @RequestParam("placement") AdPlacement placement,
            @RequestParam(name = "geoCountry", required = false) String geoCountry,
            @RequestParam(name = "geoRegion", required = false) String geoRegion,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            HttpServletRequest http) {
        Long userId = principal == null ? null : principal.userId();
        Long guestId = userId != null || guestKey == null || guestKey.isBlank()
                ? null
                : guestIdentityRepository.findByGuestKey(guestKey).map(GuestIdentity::getId).orElse(null);
        String region = regionOf(http, geoCountry, geoRegion);
        return adDeliveryService.pick(placement, region, userId, guestId)
                .map(ad -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ad))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    /** 헤더(프론트 미들웨어 → 호스팅) 순으로 국가·시도를 읽고, 없으면 요청에 실린 값을 쓴다. 시·도 이름 또는 null. */
    private static String regionOf(HttpServletRequest http, String fallbackCountry, String fallbackRegion) {
        String country = firstNonBlank(http.getHeader("X-Chak-Geo-Country"), http.getHeader("X-Vercel-IP-Country"));
        String region = firstNonBlank(http.getHeader("X-Chak-Geo-Region"), http.getHeader("X-Vercel-IP-Country-Region"));
        if (country == null && region == null) {
            country = fallbackCountry;
            region = fallbackRegion;
        }
        return AdRegions.fromGeo(country, region);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : (fallback != null && !fallback.isBlank() ? fallback : null);
    }

    /** 운영자가 올린 광고 이미지. 한 번 올린 이미지는 바뀌지 않아 오래 캐시한다. */
    @GetMapping("/creatives/{creativeId}")
    public ResponseEntity<byte[]> getCreative(@PathVariable("creativeId") Long creativeId) {
        AdCreativeService.Creative creative = adCreativeService.get(creativeId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(creative.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(creative.data());
    }

    @PostMapping("/{advertisementId}/events")
    public ResponseEntity<EventRecordedResponse> recordEvent(
            @PathVariable("advertisementId") Long advertisementId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody AdvertisementEventRequest request,
            HttpServletRequest http) {
        UserAccount user = principal == null ? null : authService.getUser(principal.userId());
        GuestSession guestSession = user == null ? guestIdentityService.resolveOrCreateLimited(guestKey, ClientIp.of(http)) : null;
        GuestIdentity guest = guestSession == null ? null : guestSession.getGuestIdentity();
        EventRecordedResponse response = advertisementService.recordEvent(
                advertisementId, user, guest, request, regionOf(http, request.geoCountry(), request.geoRegion()));

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
