package com.likelion.chak.controller;

import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.MessageResponse;
import com.likelion.chak.dto.PublicDeskResponse;
import com.likelion.chak.service.DeskService;
import com.likelion.chak.service.GuestIdentityService;
import com.likelion.chak.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/public/desks")
@RequiredArgsConstructor
public class PublicDeskController {

    private final DeskService deskService;
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
            @PathVariable("supporterToken") String supporterToken) {
        return ResponseEntity.ok(deskService.getPublicDesk(supporterToken));
    }

    @GetMapping("/{supporterToken}/messages")
    public ResponseEntity<List<MessageResponse>> getPublicMessages(
            @PathVariable("supporterToken") String supporterToken) {
        return ResponseEntity.ok(messageService.getPublicMessages(supporterToken));
    }

    @PostMapping("/{supporterToken}/messages")
    public ResponseEntity<MessageResponse> createGuestMessage(
            @PathVariable("supporterToken") String supporterToken,
            @CookieValue(name = "chak_guest_id", required = false) String guestKey,
            @RequestBody GuestMessageCreateRequest request) {
        GuestSession guestSession = guestIdentityService.resolveOrCreate(guestKey);
        MessageResponse response = messageService.createGuestMessage(
                supporterToken,
                guestSession.getGuestIdentity(),
                request);

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
