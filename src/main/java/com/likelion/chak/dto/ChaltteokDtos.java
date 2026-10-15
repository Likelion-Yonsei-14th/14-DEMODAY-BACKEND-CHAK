package com.likelion.chak.dto;

import com.likelion.chak.domain.LedgerType;
import com.likelion.chak.domain.ProductCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChaltteokDtos {

    private ChaltteokDtos() {
    }

    public record ChargeRequest(@NotNull @Min(1) @Max(1_000_000) Long amount) {
    }

    public record ChargeResponse(String orderId, long amount, String orderName) {
    }

    public record ConfirmRequest(
            @NotBlank @Size(max = 200) String paymentKey,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_=-]{6,64}$") String orderId,
            @NotNull @Min(1) @Max(1_000_000) Long amount) {
    }

    public record ConfirmResponse(String orderId, long chargedAmount, long balance) {
    }

    public record BalanceResponse(long balance, String customerKey) {
    }

    public record PurchaseItem(
            @NotNull ProductCode code,
            @Size(max = 100) String ref,
            @Min(1) @Max(100) Integer quantity) {
    }

    public record PurchaseRequest(@NotEmpty @Size(max = 20) List<@Valid PurchaseItem> items) {
    }

    public record PurchaseResponse(long spent, long balance, Long orderId) {
    }

    public record LedgerResponse(LedgerType type, long amount, long balanceAfter, String ref, Instant createdAt) {
    }
}
