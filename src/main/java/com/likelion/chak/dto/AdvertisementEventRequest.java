package com.likelion.chak.dto;

import com.likelion.chak.domain.AdEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdvertisementEventRequest(
        @NotNull AdEventType eventType,
        @NotBlank @Size(max = 100) String sessionId,
        @PositiveOrZero Long viewDurationMs,
        /** 헤더로 위치를 못 받을 때 프론트가 실어 보내는 국가·시도 코드. 없어도 된다. */
        @Size(max = 2) String geoCountry,
        @Size(max = 40) String geoRegion) {

    public AdvertisementEventRequest(AdEventType eventType, String sessionId, Long viewDurationMs) {
        this(eventType, sessionId, viewDurationMs, null, null);
    }
}
