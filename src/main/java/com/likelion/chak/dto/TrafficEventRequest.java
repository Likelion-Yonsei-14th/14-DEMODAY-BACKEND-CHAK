package com.likelion.chak.dto;

import com.likelion.chak.domain.TrafficEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record TrafficEventRequest(
        @NotBlank @Size(max = 64) String eventId,
        @NotNull TrafficEventType eventType,
        @Size(max = 100) String sessionId,
        @Size(max = 1000) String path,
        @Size(max = 1000) String referrer,
        @Size(max = 100) String resourceType,
        @Size(max = 200) String resourceKey,
        JsonNode metadata) {
}
