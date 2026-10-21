package com.likelion.chak.service;

import com.likelion.chak.domain.ClaimStatus;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.FriendDeskDtos.ClaimPreviewResponse;
import com.likelion.chak.dto.FriendDeskDtos.ClaimResultResponse;
import com.likelion.chak.dto.FriendDeskDtos.FriendDeskListResponse;
import com.likelion.chak.dto.FriendDeskDtos.FriendDeskResponse;
import com.likelion.chak.dto.OwnerDeskResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 친구를 위한 책상. 만든 사람은 넘겨받기 전까지 링크와 열람 방식, 삭제만 다룰 수 있고 응원 내용은 읽지 못한다.
 * 받는 사람은 링크로 본인임을 확인한 뒤 책상을 가져가며, 그 순간 만든 사람의 권한은 끝난다.
 */
@Service
@RequiredArgsConstructor
public class FriendDeskService {

    private static final Logger log = LoggerFactory.getLogger(FriendDeskService.class);

    /** 아직 전달하지 않은 친구 책상을 한 사람이 무한정 만들어 두는 일을 막는다. */
    static final int MAX_UNCLAIMED_PER_CREATOR = 10;

    private final DeskService deskService;
    private final PersonalDeskRepository deskRepository;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final UserAccountRepository userRepository;

    @Transactional
    public FriendDeskResponse create(UserAccount creator, DeskCreateRequest request) {
        // 같은 사람이 동시에 여러 개를 만들어 상한을 넘기지 못하게 만든 사람 행을 잠근다.
        UserAccount locked = userRepository.findForUpdateById(creator.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (deskRepository.countByCreatorIdAndClaimStatusAndDeletedAtIsNull(locked.getId(), ClaimStatus.UNCLAIMED)
                >= MAX_UNCLAIMED_PER_CREATOR) {
            throw new CustomException(ErrorCode.TOO_MANY_FRIEND_DESKS);
        }
        PersonalDesk desk = deskService.createUnclaimedDesk(
                locked,
                request.displayName().trim(),
                request.readModeType(),
                request.dailyUnlockTime(),
                request.capsuleUnlockAt());
        return FriendDeskResponse.of(desk, 0, 0);
    }

    @Transactional(readOnly = true)
    public FriendDeskListResponse listCreated(Long creatorId) {
        List<FriendDeskResponse> desks = deskRepository
                .findAllByCreatorIdAndClaimStatusAndDeletedAtIsNullOrderByCreatedAtDesc(creatorId, ClaimStatus.UNCLAIMED)
                .stream().map(this::withCounts).toList();
        return new FriendDeskListResponse(desks);
    }

    /** 받는 사람이 링크를 열었을 때. 내용은 보여 주지 않고 개수만 알려 준다. */
    @Transactional(readOnly = true)
    public ClaimPreviewResponse preview(String claimToken) {
        PersonalDesk desk = findClaimable(claimToken);
        return new ClaimPreviewResponse(
                desk.getDisplayName(),
                desk.getCreator().getDisplayName(),
                desk.getReadModeType(),
                desk.getDailyUnlockTime(),
                desk.getCapsuleUnlockAt(),
                messageCount(desk),
                friendCount(desk));
    }

    /**
     * 책상을 넘겨받는다. 본인 확인이 없으면 거절하고, 이미 책상이 있는 사람은 가져갈 수 없다(책상은 한 사람에 하나).
     * 같은 링크로 동시에 두 사람이 시도해도 책상 행을 잠가 한 명만 성공한다.
     */
    @Transactional
    public ClaimResultResponse claim(UserAccount user, String claimToken, boolean confirmedRecipient) {
        if (!confirmedRecipient) {
            throw new CustomException(ErrorCode.CLAIM_CONFIRMATION_REQUIRED);
        }
        PersonalDesk desk = deskRepository.findForUpdateByClaimToken(claimToken)
                .filter(found -> !found.isDeleted() && found.getClaimStatus() == ClaimStatus.UNCLAIMED)
                .orElseThrow(() -> new CustomException(ErrorCode.CLAIM_NOT_AVAILABLE));
        UserAccount locked = userRepository.findForUpdateById(user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (deskRepository.existsByOwnerId(locked.getId())) {
            throw new CustomException(ErrorCode.DESK_ALREADY_EXISTS);
        }
        desk.claim(locked, Instant.now());
        log.info("Desk claimed: desk={} by={}", desk.getId(), locked.getId());
        return new ClaimResultResponse(OwnerDeskResponse.from(desk), messageCount(desk), friendCount(desk));
    }

    /**
     * 현재 주인이 책상을 지운다. 넘겨받기 전에는 만든 사람, 넘겨받은 뒤에는 받은 사람이 주인이다.
     * 주인이 아닌 사람에게는 책상이 있는지조차 알리지 않는다.
     */
    @Transactional
    public void delete(Long userId, Long deskId) {
        PersonalDesk desk = deskRepository.findForUpdateById(deskId)
                .filter(found -> !found.isDeleted())
                .filter(found -> isCurrentOwner(found, userId))
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
        desk.softDelete(Instant.now());
        log.info("Desk deleted: desk={} by={}", deskId, userId);
    }

    private static boolean isCurrentOwner(PersonalDesk desk, Long userId) {
        if (desk.getClaimStatus() == ClaimStatus.UNCLAIMED) {
            return desk.getCreator().getId().equals(userId);
        }
        return desk.getOwner() != null && desk.getOwner().getId().equals(userId);
    }

    private PersonalDesk findClaimable(String claimToken) {
        return deskRepository.findByClaimToken(claimToken)
                .filter(desk -> !desk.isDeleted() && desk.getClaimStatus() == ClaimStatus.UNCLAIMED)
                .orElseThrow(() -> new CustomException(ErrorCode.CLAIM_NOT_AVAILABLE));
    }

    private FriendDeskResponse withCounts(PersonalDesk desk) {
        return FriendDeskResponse.of(desk, messageCount(desk), friendCount(desk));
    }

    private long messageCount(PersonalDesk desk) {
        return deliveryRepository.countByDeskAndMessageStatusNot(desk, MessageStatus.DELETED);
    }

    private long friendCount(PersonalDesk desk) {
        return deliveryRepository.countDistinctAuthors(desk, MessageStatus.DELETED);
    }
}
