package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.RecapDtos.Recap;
import com.likelion.chak.service.IdentityService;
import com.likelion.chak.service.RecapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 나의 수능 리캡. 회원은 받은·보낸 응원과 미니게임을, 비회원은 쿠키로 보낸 응원만 센다. */
@RestController
@RequestMapping("/api/public/recap")
@RequiredArgsConstructor
public class RecapController {

    private final IdentityService identityService;
    private final RecapService recapService;

    @GetMapping
    public ResponseEntity<Recap> recap(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recapService.recap(identityService.resolve(principal, guestKey)));
    }
}
