package com.likelion.chak.dto;

import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.MessageVisibility;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class PublicDeskResponse {

    private Long id;
    private String displayName;
    private ReadModeType readModeType;
    private LocalTime dailyUnlockTime;
    private Instant capsuleUnlockAt;
    private boolean publicFeedEnabled;
    private MessageVisibility defaultMessageVisibility;
    private boolean roomClosed;

    public static PublicDeskResponse from(PersonalDesk desk) {
        return new PublicDeskResponse(
                desk.getId(),
                desk.getDisplayName(),
                desk.getReadModeType(),
                desk.getDailyUnlockTime(),
                desk.getCapsuleUnlockAt(),
                desk.isPublicFeedEnabled(),
                desk.getDefaultMessageVisibility(),
                desk.isRoomClosed());
    }
}
