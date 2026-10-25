package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.push.PushService;
import com.likelion.chak.service.CallerIdentity;
import com.likelion.chak.service.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 답장 알림(웹 푸시) 구독. 응원을 남긴 로그인 회원 또는 같은 브라우저의 비회원이 쓴다. */
@RestController
@RequestMapping("/api/public/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;
    private final IdentityService identityService;

    public record KeyResponse(boolean enabled, String publicKey) {
    }

    public record Keys(@NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 40) String auth) {
    }

    public record SubscribeRequest(@NotBlank @Size(max = 500) String endpoint, @Valid @jakarta.validation.constraints.NotNull Keys keys) {
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 500) String endpoint) {
    }

    /** 알림을 쓸 수 있는지와 브라우저가 구독할 때 필요한 공개키. */
    @GetMapping("/key")
    public ResponseEntity<KeyResponse> key() {
        return ResponseEntity.ok(new KeyResponse(pushService.isEnabled(), pushService.isEnabled() ? pushService.publicKey() : null));
    }

    @PutMapping("/subscription")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody SubscribeRequest request) {
        CallerIdentity caller = identityService.requireIdentity(principal, guestKey);
        pushService.subscribe(caller, request.endpoint(), request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscription")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody UnsubscribeRequest request) {
        CallerIdentity caller = identityService.resolve(principal, guestKey);
        if (caller.isPresent()) {
            pushService.unsubscribe(caller, request.endpoint());
        }
        return ResponseEntity.noContent().build();
    }
}
