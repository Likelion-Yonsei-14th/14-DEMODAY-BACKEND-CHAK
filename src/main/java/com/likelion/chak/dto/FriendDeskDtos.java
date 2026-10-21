package com.likelion.chak.dto;

import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class FriendDeskDtos {

    private FriendDeskDtos() {
    }

    /** 만든 사람에게 보여 주는 정보. 응원 내용은 없고 개수만 있다. */
    public record FriendDeskResponse(
            Long id,
            String displayName,
            String supporterToken,
            String claimToken,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt,
            boolean roomClosed,
            long messageCount,
            long friendCount,
            Instant createdAt) {

        public static FriendDeskResponse of(PersonalDesk desk, long messageCount, long friendCount) {
            return new FriendDeskResponse(
                    desk.getId(), desk.getDisplayName(), desk.getSupporterToken(), desk.getClaimToken(),
                    desk.getReadModeType(), desk.getDailyUnlockTime(), desk.getCapsuleUnlockAt(),
                    desk.isRoomClosed(), messageCount, friendCount, desk.getCreatedAt());
        }
    }

    /** 받는 사람이 링크를 열었을 때 보는 정보. 누구를 위한 책상인지와 응원이 얼마나 모였는지만 알려 준다. */
    public record ClaimPreviewResponse(
            String displayName,
            String creatorName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt,
            long messageCount,
            long friendCount) {
    }

    /** 수령자가 본인임을 확인했다는 표시. 값이 true가 아니면 넘겨받을 수 없다. */
    public record ClaimRequest(Boolean confirmedRecipient) {
    }

    public record ClaimResultResponse(
            OwnerDeskResponse desk,
            long messageCount,
            long friendCount) {
    }

    public record FriendDeskListResponse(List<FriendDeskResponse> desks) {
    }
}
