package com.likelion.chak.dto;

import com.likelion.chak.domain.AdPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AdvertisementRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String creativeUrl,
        @NotBlank @Size(max = 1000) String destinationUrl,
        @NotNull AdPlacement placement,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        boolean active) {
}
