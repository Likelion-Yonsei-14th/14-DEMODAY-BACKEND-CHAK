package com.likelion.chak.dto;

import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;

import java.time.Instant;
import java.time.LocalTime;

public record OwnerDeskResponse(
        Long id,
        String displayName,
        String supporterToken,
        ReadModeType readModeType,
        LocalTime dailyUnlockTime,
        Instant capsuleUnlockAt,
        boolean publicFeedEnabled,
        MessageVisibility defaultMessageVisibility,
        boolean roomClosed,
        Instant createdAt,
        Instant updatedAt) {

    public static OwnerDeskResponse from(PersonalDesk desk) {
        return new OwnerDeskResponse(
                desk.getId(),
                desk.getDisplayName(),
                desk.getSupporterToken(),
                desk.getReadModeType(),
                desk.getDailyUnlockTime(),
                desk.getCapsuleUnlockAt(),
                desk.isPublicFeedEnabled(),
                desk.getDefaultMessageVisibility(),
                desk.isRoomClosed(),
                desk.getCreatedAt(),
                desk.getUpdatedAt());
    }
}
