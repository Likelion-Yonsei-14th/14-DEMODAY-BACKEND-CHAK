package com.likelion.chak.service;

import com.likelion.chak.domain.AdEventType;
import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.AdUnlock;
import com.likelion.chak.dto.PaymentDtos.UnlockRequest;
import com.likelion.chak.dto.PaymentDtos.UnlockResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdUnlockRepository;
import com.likelion.chak.repository.AdvertisementEventRepository;
import com.likelion.chak.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 광고를 끝까지 본 사람만 편지지·꾸미기 소품을 해제한다. 해제를 위한 광고가 하나도 등록돼 있지 않으면
 * 사용자를 막지 않기 위해 광고 없이 해제를 허용한다.
 */
@Service
@RequiredArgsConstructor
public class UnlockService {

    private final AdUnlockRepository unlockRepository;
    private final AdvertisementRepository advertisementRepository;
    private final AdvertisementEventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<UnlockResponse> list(CallerIdentity caller) {
        if (!caller.isPresent()) {
            return List.of();
        }
        List<AdUnlock> unlocks = caller.user() != null
                ? unlockRepository.findAllByUserId(caller.userId())
                : unlockRepository.findAllByGuestId(caller.guestId());
        return unlocks.stream().map(u -> new UnlockResponse(u.getCategory(), u.getAssetId())).toList();
    }

    @Transactional
    public UnlockResponse unlock(CallerIdentity caller, UnlockRequest request) {
        if (!caller.isPresent()) {
            throw new CustomException(ErrorCode.IDENTITY_REQUIRED);
        }
        boolean already = caller.user() != null
                ? unlockRepository.existsByUserIdAndCategoryAndAssetId(caller.userId(), request.category(), request.assetId())
                : unlockRepository.existsByGuestIdAndCategoryAndAssetId(caller.guestId(), request.category(), request.assetId());
        if (already) {
            return new UnlockResponse(request.category(), request.assetId());
        }
        requireAdIfAvailable(caller, request.adSessionId());
        unlockRepository.save(AdUnlock.create(
                caller.user(), caller.guest(), request.category(), request.assetId(), request.adSessionId()));
        return new UnlockResponse(request.category(), request.assetId());
    }

    private void requireAdIfAvailable(CallerIdentity caller, String adSessionId) {
        Instant now = Instant.now();
        boolean inventory = !advertisementRepository
                .findAllByPlacementAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByIdDesc(
                        AdPlacement.UNLOCK, now, now).isEmpty();
        if (!inventory) {
            return;
        }
        boolean watched = adSessionId != null && !adSessionId.isBlank() && (caller.user() != null
                ? eventRepository.existsByEventTypeAndSessionIdAndUserId(
                        AdEventType.VIEW_COMPLETED, adSessionId, caller.userId())
                : eventRepository.existsByEventTypeAndSessionIdAndGuestId(
                        AdEventType.VIEW_COMPLETED, adSessionId, caller.guestId()));
        if (!watched) {
            throw new CustomException(ErrorCode.AD_NOT_COMPLETED);
        }
    }
}
