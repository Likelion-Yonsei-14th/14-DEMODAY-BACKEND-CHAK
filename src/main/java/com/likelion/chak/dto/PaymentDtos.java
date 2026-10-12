package com.likelion.chak.dto;

import com.likelion.chak.domain.OrderStatus;
import com.likelion.chak.domain.ProductCode;
import com.likelion.chak.domain.UnlockCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record OrderItemRequest(
            @NotNull ProductCode code,
            @Size(max = 100) String ref,
            @Min(1) @Max(100) Integer quantity) {
    }

    public record OrderCreateRequest(@NotEmpty @Size(max = 20) List<@Valid OrderItemRequest> items) {
    }

    public record OrderItemResponse(ProductCode code, String ref, int quantity, int unitPrice, int amount) {
    }

    public record OrderResponse(
            Long orderId,
            OrderStatus status,
            int totalAmount,
            boolean consumed,
            List<OrderItemResponse> items,
            Instant createdAt,
            Instant paidAt) {
    }

    public record UnlockRequest(
            @NotNull UnlockCategory category,
            @NotBlank @Size(max = 100) String assetId,
            @Size(max = 100) String adSessionId) {
    }

    public record UnlockResponse(UnlockCategory category, String assetId) {
    }
}
