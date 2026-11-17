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
        boolean active,
        /** 대상 시·도 이름("서울", "경기"). 비우거나 없으면 전국. */
        @Size(max = 17) java.util.List<@Size(max = 10) String> targetRegions) {

    public AdvertisementRequest(
            String title, String creativeUrl, String destinationUrl, AdPlacement placement,
            Instant startsAt, Instant endsAt, boolean active) {
        this(title, creativeUrl, destinationUrl, placement, startsAt, endsAt, active, null);
    }
}
