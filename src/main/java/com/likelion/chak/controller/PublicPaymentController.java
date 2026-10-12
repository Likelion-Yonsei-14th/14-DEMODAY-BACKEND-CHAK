package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.PaymentDtos.OrderCreateRequest;
import com.likelion.chak.dto.PaymentDtos.OrderResponse;
import com.likelion.chak.dto.PaymentDtos.UnlockRequest;
import com.likelion.chak.dto.PaymentDtos.UnlockResponse;
import com.likelion.chak.service.CallerIdentity;
import com.likelion.chak.service.GuestCookieService;
import com.likelion.chak.service.IdentityService;
import com.likelion.chak.service.PaymentService;
import com.likelion.chak.service.UnlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 결제와 광고 해제. 로그인하지 않은 방문자도 쿠키로 식별해 이용할 수 있다. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicPaymentController {

    private final IdentityService identityService;
    private final GuestCookieService guestCookieService;
    private final PaymentService paymentService;
    private final UnlockService unlockService;

    @PostMapping("/payments/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody OrderCreateRequest request) {
        Writer writer = writer(principal, guestKey);
        OrderResponse response = paymentService.createOrder(writer.identity, request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        GuestCookieService.addHeader(builder, writer.cookie);
        return builder.body(response);
    }

    @PostMapping("/payments/orders/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(paymentService.confirm(identityService.requireIdentity(principal, guestKey), orderId));
    }

    @GetMapping("/payments/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(paymentService.get(identityService.requireIdentity(principal, guestKey), orderId));
    }

    @GetMapping("/unlocks")
    public ResponseEntity<List<UnlockResponse>> unlocks(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey) {
        return ResponseEntity.ok(unlockService.list(identityService.resolve(principal, guestKey)));
    }

    @PostMapping("/unlocks")
    public ResponseEntity<UnlockResponse> unlock(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody UnlockRequest request) {
        Writer writer = writer(principal, guestKey);
        UnlockResponse response = unlockService.unlock(writer.identity, request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        GuestCookieService.addHeader(builder, writer.cookie);
        return builder.body(response);
    }

    private Writer writer(AuthenticatedUser principal, String guestKey) {
        if (principal != null) {
            return new Writer(identityService.resolve(principal, null), null);
        }
        GuestSession session = guestCookieService.resolveOrCreate(guestKey);
        return new Writer(new CallerIdentity(null, session.getGuestIdentity()),
                guestCookieService.cookieHeaderIfCreated(session));
    }

    private record Writer(CallerIdentity identity, String cookie) {
    }
}
