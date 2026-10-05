package com.likelion.chak.dto;

import java.time.Instant;
import java.util.Map;

public record AnalyticsSummaryResponse(
        Instant from,
        Instant to,
        Map<String, Long> trafficEvents,
        Map<String, Long> advertisementEvents) {
}
