package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.domain.TrafficEventType;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.MessageResponse;
import com.likelion.chak.dto.PublicDeskResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.service.DeskService;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.AnalyticsService;
import com.likelion.chak.service.GuestIdentityService;
import com.likelion.chak.service.MessageService;
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

@RestController
@RequestMapping("/api/public/desks")
@RequiredArgsConstructor
public class PublicDeskController {

    private final DeskService deskService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final GuestIdentityService guestIdentityService;
    private final MessageService messageService;

    @Value("${chak.guest-cookie.name}")
    private String guestCookieName;

    @Value("${chak.guest-cookie.max-age-seconds}")
    private long guestCookieMaxAgeSeconds;

    @Value("${chak.guest-cookie.secure}")
    private boolean guestCookieSecure;

    @GetMapping("/{supporterToken}")
    public ResponseEntity<PublicDeskResponse> getDesk(
            @PathVariable("supporterToken") String supporterToken,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey) {
        PublicDeskResponse response = deskService.getPublicDesk(supporterToken);
        UserAccount user = authenticatedUser == null
                ? null
                : authService.getUser(authenticatedUser.userId());
        GuestSession guestSession = user == null ? guestIdentityService.resolveOrCreate(guestKey) : null;
        analyticsService.recordSystem(
                user,
                guestSession == null ? null : guestSession.getGuestIdentity(),
                TrafficEventType.INVITE_LINK_OPEN,
                "PERSONAL_DESK",
                supporterToken,
                "/public/desks/" + supporterToken);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (guestSession != null && guestSession.isCreated()) {
            builder.header(HttpHeaders.SET_COOKIE, createGuestCookie(
                    guestSession.getGuestIdentity().getGuestKey()).toString());
        }
        return builder.body(response);
    }

    @GetMapping("/{supporterToken}/messages")
    public ResponseEntity<SliceResponse<MessageResponse>> getPublicMessages(
            @PathVariable("supporterToken") String supporterToken,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(messageService.getPublicMessages(supporterToken, page, size));
    }

    @PostMapping("/{supporterToken}/messages")
    public ResponseEntity<MessageResponse> createGuestMessage(
            @PathVariable("supporterToken") String supporterToken,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody GuestMessageCreateRequest request) {
        if (authenticatedUser != null) {
            UserAccount user = authService.getUser(authenticatedUser.userId());
            MessageResponse response = messageService.createUserMessage(supporterToken, user, request);
            analyticsService.recordSystem(
                    user, null, TrafficEventType.MESSAGE_CREATED,
                    "PERSONAL_DESK", supporterToken, "/public/desks/" + supporterToken + "/messages");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        GuestSession guestSession = guestIdentityService.resolveOrCreate(guestKey);
        MessageResponse response = messageService.createGuestMessage(
                supporterToken,
                guestSession.getGuestIdentity(),
                request);
        analyticsService.recordSystem(
                null,
                guestSession.getGuestIdentity(),
                TrafficEventType.MESSAGE_CREATED,
                "PERSONAL_DESK",
                supporterToken,
                "/public/desks/" + supporterToken + "/messages");

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.CREATED);
        if (guestSession.isCreated()) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, createGuestCookie(
                    guestSession.getGuestIdentity().getGuestKey()).toString());
        }

        return responseBuilder.body(response);
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
