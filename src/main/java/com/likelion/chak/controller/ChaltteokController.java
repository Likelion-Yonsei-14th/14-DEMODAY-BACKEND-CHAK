package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.ChaltteokDtos.BalanceResponse;
import com.likelion.chak.dto.ChaltteokDtos.ChargeRequest;
import com.likelion.chak.dto.ChaltteokDtos.ChargeResponse;
import com.likelion.chak.dto.ChaltteokDtos.ConfirmRequest;
import com.likelion.chak.dto.ChaltteokDtos.ConfirmResponse;
import com.likelion.chak.dto.ChaltteokDtos.LedgerResponse;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseRequest;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.ChaltteokService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 찰떡(충전식 포인트). 회원 전용이다. */
@RestController
@RequestMapping("/api/chaltteok")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ChaltteokController {

    private final ChaltteokService chaltteokService;

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> balance(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(chaltteokService.balance(principal.userId()));
    }

    @PostMapping("/charges")
    public ResponseEntity<ChargeResponse> createCharge(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chaltteokService.createCharge(principal.userId(), request.amount()));
    }

    @PostMapping("/charges/confirm")
    public ResponseEntity<ConfirmResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(chaltteokService.confirmCharge(
                principal.userId(), request.paymentKey(), request.orderId(), request.amount()));
    }

    @PostMapping("/purchases")
    public ResponseEntity<PurchaseResponse> purchase(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseRequest request) {
        if (idempotencyKey != null && !idempotencyKey.matches("^[A-Za-z0-9_-]{8,64}$")) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(chaltteokService.purchase(principal.userId(), request.items(), idempotencyKey));
    }

    @GetMapping("/history")
    public ResponseEntity<List<LedgerResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "size", defaultValue = "30") int size) {
        return ResponseEntity.ok(chaltteokService.history(principal.userId(), Math.max(1, Math.min(size, 100))));
    }
}
