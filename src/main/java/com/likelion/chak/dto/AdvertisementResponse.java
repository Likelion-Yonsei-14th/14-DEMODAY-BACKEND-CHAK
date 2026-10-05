package com.likelion.chak.dto;

import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.Advertisement;

import java.time.Instant;

public record AdvertisementResponse(
        Long id,
        String title,
        String creativeUrl,
        String destinationUrl,
        AdPlacement placement,
        Instant startsAt,
        Instant endsAt,
        boolean active) {

    public static AdvertisementResponse from(Advertisement advertisement) {
        return new AdvertisementResponse(
                advertisement.getId(),
                advertisement.getTitle(),
                advertisement.getCreativeUrl(),
                advertisement.getDestinationUrl(),
                advertisement.getPlacement(),
                advertisement.getStartsAt(),
                advertisement.getEndsAt(),
                advertisement.isActive());
    }
}
