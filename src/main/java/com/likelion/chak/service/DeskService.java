package com.likelion.chak.service;

import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.DeskSettingsRequest;
import com.likelion.chak.dto.OwnerDeskResponse;
import com.likelion.chak.dto.PublicDeskResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.repository.MessageRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class DeskService {

    private final PersonalDeskRepository personalDeskRepository;
    private final UserAccountRepository userAccountRepository;
    private final MessageRepository messageRepository;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final UnlockTimeCalculator unlockTimeCalculator;

    @Transactional
    public OwnerDeskResponse createMyDesk(UserAccount owner, DeskCreateRequest request) {
        UserAccount lockedOwner = userAccountRepository.findForUpdateById(owner.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (personalDeskRepository.existsByOwnerId(lockedOwner.getId())) {
            throw new CustomException(ErrorCode.DESK_ALREADY_EXISTS);
        }

        validateReadMode(request.readModeType(), request.dailyUnlockTime(), request.capsuleUnlockAt());
        PersonalDesk desk = PersonalDesk.createClaimed(
                lockedOwner,
                request.displayName().trim(),
                request.readModeType(),
                request.dailyUnlockTime(),
                request.capsuleUnlockAt());
        desk.updateSettings(
                request.displayName().trim(),
                request.readModeType(),
                request.dailyUnlockTime(),
                request.capsuleUnlockAt(),
                request.publicFeedEnabled() == null || request.publicFeedEnabled(),
                request.defaultMessageVisibility() == null
                        ? MessageVisibility.PRIVATE
                        : request.defaultMessageVisibility());
        return OwnerDeskResponse.from(personalDeskRepository.save(desk));
    }

    @Transactional(readOnly = true)
    public OwnerDeskResponse getMyDesk(Long ownerId) {
        return OwnerDeskResponse.from(findByOwnerId(ownerId));
    }

    @Transactional
    public OwnerDeskResponse updateMyDesk(Long ownerId, DeskSettingsRequest request) {
        validateReadMode(request.readModeType(), request.dailyUnlockTime(), request.capsuleUnlockAt());
        PersonalDesk desk = findByOwnerIdForUpdate(ownerId);
        desk.updateSettings(
                request.displayName().trim(),
                request.readModeType(),
                request.dailyUnlockTime(),
                request.capsuleUnlockAt(),
                request.publicFeedEnabled(),
                request.defaultMessageVisibility());

        if (request.pushEnabled() != null) {
            desk.changePushEnabled(request.pushEnabled());
        }
        rescheduleLockedMessages(desk);

        if (request.applyVisibilityToExisting()) {
            messageRepository.updateVisibilityByDeskAndCreatedAtLessThanEqual(
                    desk.getId(),
                    request.defaultMessageVisibility(),
                    MessageStatus.DELETED,
                    Instant.now());
        }
        return OwnerDeskResponse.from(desk);
    }

    /**
     * 공개 방식이 바뀌면 아직 열리지 않은 편지의 공개 시각을 새 설정으로 다시 계산한다.
     * 이미 열린 편지는 다시 잠그지 않는다.
     */
    private void rescheduleLockedMessages(PersonalDesk desk) {
        Instant now = Instant.now();
        deliveryRepository.findAllByDeskAndUnlockAtAfterAndMessageStatusNot(desk, now, MessageStatus.DELETED)
                .forEach(delivery -> delivery.reschedule(
                        unlockTimeCalculator.calculate(desk, delivery.getMessage().getCreatedAt())));
    }

    @Transactional
    public OwnerDeskResponse setRoomClosed(Long ownerId, boolean closed) {
        PersonalDesk desk = findByOwnerIdForUpdate(ownerId);
        if (closed) {
            desk.closeRoom();
        } else {
            desk.openRoom();
        }
        return OwnerDeskResponse.from(desk);
    }

    @Transactional(readOnly = true)
    public PersonalDesk findByOwnerId(Long ownerId) {
        return personalDeskRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
    }

    @Transactional
    public PersonalDesk createClaimedDesk(
            UserAccount owner,
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        validateReadMode(readModeType, dailyUnlockTime, capsuleUnlockAt);

        UserAccount lockedOwner = userAccountRepository.findForUpdateById(owner.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (personalDeskRepository.existsByOwnerId(lockedOwner.getId())) {
            throw new CustomException(ErrorCode.DESK_ALREADY_EXISTS);
        }

        PersonalDesk desk = PersonalDesk.createClaimed(
                lockedOwner,
                displayName,
                readModeType,
                dailyUnlockTime,
                capsuleUnlockAt);

        return personalDeskRepository.save(desk);
    }

    @Transactional
    public PersonalDesk createUnclaimedDesk(
            UserAccount creator,
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        validateReadMode(readModeType, dailyUnlockTime, capsuleUnlockAt);

        PersonalDesk desk = PersonalDesk.createUnclaimed(
                creator,
                displayName,
                readModeType,
                dailyUnlockTime,
                capsuleUnlockAt);

        return personalDeskRepository.save(desk);
    }

    @Transactional(readOnly = true)
    public PublicDeskResponse getPublicDesk(String supporterToken) {
        return PublicDeskResponse.from(findBySupporterToken(supporterToken));
    }

    @Transactional(readOnly = true)
    public PersonalDesk findBySupporterToken(String supporterToken) {
        return personalDeskRepository.findBySupporterTokenAndDeletedAtIsNull(supporterToken)
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
    }

    @Transactional
    public PersonalDesk findBySupporterTokenForUpdate(String supporterToken) {
        return personalDeskRepository.findForUpdateBySupporterTokenAndDeletedAtIsNull(supporterToken)
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
    }

    private PersonalDesk findByOwnerIdForUpdate(Long ownerId) {
        return personalDeskRepository.findForUpdateByOwnerId(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
    }

    private void validateReadMode(
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        if (readModeType == ReadModeType.DAILY && dailyUnlockTime == null) {
            throw new CustomException(ErrorCode.INVALID_DESK_SETTINGS);
        }
        if (readModeType == ReadModeType.TIME_CAPSULE && capsuleUnlockAt == null) {
            throw new CustomException(ErrorCode.INVALID_DESK_SETTINGS);
        }
    }
}
