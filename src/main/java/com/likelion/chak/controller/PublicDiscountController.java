package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.DiscountEvent;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.dto.DiscountDtos.DiscountEventRequest;
import com.likelion.chak.dto.DiscountDtos.DiscountList;
import com.likelion.chak.dto.DiscountDtos.SponsoredOrder;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.repository.GuestIdentityRepository;
import com.likelion.chak.service.DiscountAdService;
import com.likelion.chak.service.DiscountService;
import com.likelion.chak.service.GuestIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
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

/** 수험표 할인 모음(혜택함). 로그인 없이 본다. 운영자가 고친 내용이 곧 보이도록 캐시는 짧게. */
@RestController
@RequestMapping("/api/public/discounts")
@RequiredArgsConstructor
public class PublicDiscountController {

    private final DiscountService discountService;
    private final DiscountAdService discountAdService;
    private final GuestIdentityRepository guestIdentityRepository;
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
    public ResponseEntity<DiscountList> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(discountService.listPublic());
    }

    /**
     * 이 사람에게 보여 줄 제휴 혜택 순서({@link DiscountAdService}). 사람마다 다르므로 캐시하지 않는다.
     * lat/lng는 사용자가 "내 주변"을 눌러 알려 준 위치(저장하지 않는다). 없으면 접속 지역으로 맞춘다.
     */
    @GetMapping("/sponsored")
    public ResponseEntity<SponsoredOrder> sponsored(
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "geoCountry", required = false) String geoCountry,
            @RequestParam(name = "geoRegion", required = false) String geoRegion,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            HttpServletRequest http) {
        Long userId = principal == null ? null : principal.userId();
        Long guestId = userId != null || guestKey == null || guestKey.isBlank()
                ? null
                : guestIdentityRepository.findByGuestKey(guestKey).map(GuestIdentity::getId).orElse(null);
        boolean validPoint = lat != null && lng != null && Double.isFinite(lat) && Double.isFinite(lng)
                && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
        String region = PublicAdvertisementController.regionOf(http, geoCountry, geoRegion);
        SponsoredOrder order = discountAdService.order(validPoint ? lat : null, validPoint ? lng : null, region, userId, guestId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(order);
    }

    /** 제휴 혜택 노출·클릭 한 건. 손님이면 손님 쿠키를 만들어 같은 사람을 알아본다. */
    @PostMapping("/{discountId}/events")
    public ResponseEntity<Void> recordEvent(
            @PathVariable("discountId") Long discountId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody DiscountEventRequest request,
            HttpServletRequest http) {
        Long userId = principal == null ? null : principal.userId();
        GuestSession guestSession = userId == null ? guestIdentityService.resolveOrCreateLimited(guestKey, ClientIp.of(http)) : null;
        GuestIdentity guest = guestSession == null ? null : guestSession.getGuestIdentity();
        boolean recorded = discountAdService.record(discountId, request.type(),
                request.placement() == null ? DiscountEvent.Placement.LIST : request.placement(),
                userId, guest == null ? null : guest.getId(),
                PublicAdvertisementController.regionOf(http, request.geoCountry(), request.geoRegion()));
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(recorded ? HttpStatus.CREATED : HttpStatus.ACCEPTED);
        if (guestSession != null && guestSession.isCreated()) {
            builder.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(guestCookieName, guest.getGuestKey())
                    .httpOnly(true).secure(guestCookieSecure).sameSite(guestCookieSameSite).path("/")
                    .maxAge(Duration.ofSeconds(guestCookieMaxAgeSeconds)).build().toString());
        }
        return builder.cacheControl(CacheControl.noStore()).build();
    }
}
