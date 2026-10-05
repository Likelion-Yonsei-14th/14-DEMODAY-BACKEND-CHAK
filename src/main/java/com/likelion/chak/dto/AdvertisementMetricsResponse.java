package com.likelion.chak.dto;

import java.time.Instant;
import java.util.Map;

public record AdvertisementMetricsResponse(
        Long advertisementId,
        Instant from,
        Instant to,
        Map<String, Long> events) {
}
