package com.likelion.chak.controller;

import com.likelion.chak.alert.MinigameRateLimiter;
import com.likelion.chak.alert.MinigameRateLimiter.Action;
import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.PollDtos.PollList;
import com.likelion.chak.dto.PollDtos.PollView;
import com.likelion.chak.dto.PollDtos.VoteRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.GuestIdentityRepository;
import com.likelion.chak.service.AnalyticsService;
import com.likelion.chak.service.GuestIdentityService;
import com.likelion.chak.service.PollService;
import com.likelion.chak.service.RegionNames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/** 수능 여론 투표. 로그인 없이 쓰고, 회원이면 회원 번호로, 아니면 손님 쿠키로 한 사람을 구분한다. */
@RestController
@RequestMapping("/api/public/polls")
@RequiredArgsConstructor
public class PublicPollController {

    private final PollService pollService;
    private final GuestIdentityService guestIdentityService;
    private final GuestIdentityRepository guestIdentityRepository;
    private final MinigameRateLimiter rateLimiter;

    @Value("${chak.guest-cookie.name}")
    private String guestCookieName;

    @Value("${chak.guest-cookie.max-age-seconds}")
    private long guestCookieMaxAgeSeconds;

    @Value("${chak.guest-cookie.secure}")
    private boolean guestCookieSecure;

    @Value("${chak.guest-cookie.same-site:Lax}")
    private String guestCookieSameSite;

    @GetMapping
    public ResponseEntity<PollList> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey) {
        String voterKey;
        if (principal != null) {
            voterKey = "u:" + principal.userId();
        } else if (guestKey != null && !guestKey.isBlank()) {
            // 목록을 볼 때는 손님을 새로 만들지 않는다. 이미 있는 손님이면 고른 답을 같이 보여 준다.
            voterKey = guestIdentityRepository.findByGuestKey(guestKey).map(g -> "g:" + g.getId()).orElse(null);
        } else {
            voterKey = null;
        }
        return ResponseEntity.ok(pollService.list(voterKey, Instant.now()));
    }

    @PostMapping("/{pollId}/votes")
    public ResponseEntity<PollView> vote(
            @PathVariable long pollId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @RequestHeader(name = "X-Chak-Geo-Country", required = false) String chakCountry,
            @RequestHeader(name = "X-Chak-Geo-Region", required = false) String chakRegion,
            @RequestHeader(name = "X-Vercel-IP-Country", required = false) String vercelCountry,
            @RequestHeader(name = "X-Vercel-IP-Country-Region", required = false) String vercelRegion,
            @Valid @RequestBody VoteRequest request,
            HttpServletRequest http) {
        String ip = ClientIp.of(http);
        if (!rateLimiter.tryAcquire(Action.POLL_VOTE, "ip" + ip)
                || (principal == null && !rateLimiter.tryAcquire(Action.POLL_VOTE_PER_POLL, "ip" + ip + ":poll" + pollId))) {
            throw new CustomException(ErrorCode.TOO_MANY_VOTES);
        }
        GuestSession guest = null;
        String voterKey;
        if (principal != null) {
            voterKey = "u:" + principal.userId();
        } else {
            guest = guestIdentityService.resolveOrCreateLimited(guestKey, ip);
            voterKey = "g:" + guest.getGuestIdentity().getId();
        }
        AnalyticsService.Geo geo = AnalyticsService.Geo.fromHeaders(
                firstNonBlank(chakCountry, vercelCountry), firstNonBlank(chakRegion, vercelRegion));
        if (geo == null) {
            geo = AnalyticsService.Geo.fromHeaders(request.geoCountry(), request.geoRegion());
        }
        String region = geo == null || !"KR".equals(geo.country()) || geo.region() == null
                ? null : RegionNames.of(geo.country(), geo.region());
        PollView view = pollService.vote(pollId, request.optionId(), voterKey, region, Instant.now());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (guest != null && guest.isCreated()) {
            builder.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(guestCookieName, guest.getGuestIdentity().getGuestKey())
                    .httpOnly(true).secure(guestCookieSecure).sameSite(guestCookieSameSite).path("/")
                    .maxAge(Duration.ofSeconds(guestCookieMaxAgeSeconds)).build().toString());
        }
        return builder.body(view);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
