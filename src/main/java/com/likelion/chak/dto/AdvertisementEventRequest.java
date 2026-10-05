package com.likelion.chak.dto;

import com.likelion.chak.domain.AdEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdvertisementEventRequest(
        @NotNull AdEventType eventType,
        @NotBlank @Size(max = 100) String sessionId,
        @PositiveOrZero Long viewDurationMs) {
}
