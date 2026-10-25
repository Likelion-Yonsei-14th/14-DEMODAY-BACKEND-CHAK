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
        JsonNode metadata,
        /** 프론트 호스팅이 알려 준 방문자 국가(2자)와 시·도 코드. 요청 헤더에 위치가 없을 때만 쓴다. */
        @Size(max = 2) String geoCountry,
        @Size(max = 40) String geoRegion) {

    /** 위치 없이 만드는 기존 형태. */
    public TrafficEventRequest(
            String eventId, TrafficEventType eventType, String sessionId, String path,
            String referrer, String resourceType, String resourceKey, JsonNode metadata) {
        this(eventId, eventType, sessionId, path, referrer, resourceType, resourceKey, metadata, null, null);
    }
}
