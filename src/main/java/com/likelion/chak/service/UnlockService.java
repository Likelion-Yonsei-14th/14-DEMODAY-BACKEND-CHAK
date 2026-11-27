package com.likelion.chak.service;

import com.likelion.chak.domain.AdEventType;
import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.AdUnlock;
import com.likelion.chak.domain.AdvertisementEvent;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 광고를 끝까지 본 사람만 편지지·꾸미기 소품을 해제한다. 해제를 위한 광고가 하나도 등록돼 있지 않으면
 * 사용자를 막지 않기 위해 광고 없이 해제를 허용한다.
 */
@Service
@RequiredArgsConstructor
public class UnlockService {

    /** 프론트 해제 광고는 5초 동안 재생된다. 네트워크 지연을 감안해 시작·완료 기록 사이 최소 간격을 조금 짧게 둔다. */
    static final Duration MIN_AD_VIEW = Duration.ofSeconds(4);

    private final AdUnlockRepository unlockRepository;
    private final AdvertisementRepository advertisementRepository;
    private final AdSwitchService adSwitchService;
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
        if (!adSwitchService.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        boolean inventory = !advertisementRepository
                .findAllByPlacementAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByIdDesc(
                        AdPlacement.UNLOCK, now, now).isEmpty();
        if (!inventory) {
            return;
        }
        if (adSessionId == null || adSessionId.isBlank()) {
            throw new CustomException(ErrorCode.AD_NOT_COMPLETED);
        }
        String session = adSessionId.trim();
        // 한 번 본 광고로는 하나만 해제한다(같은 회차를 여러 해제에 다시 쓰지 못하게).
        boolean used = caller.user() != null
                ? unlockRepository.existsByUserIdAndAdSessionId(caller.userId(), session)
                : unlockRepository.existsByGuestIdAndAdSessionId(caller.guestId(), session);
        // 해제 광고(UNLOCK 자리)의 시작과 완료 기록이 모두 있어야 하고, 서버가 찍은 두 시각 사이가 광고 길이만큼 벌어져야 한다.
        Optional<AdvertisementEvent> started = viewEvent(caller, AdEventType.VIEW_STARTED, session);
        Optional<AdvertisementEvent> completed = viewEvent(caller, AdEventType.VIEW_COMPLETED, session);
        boolean watched = !used && started.isPresent() && completed.isPresent()
                && !completed.get().getCreatedAt().isBefore(started.get().getCreatedAt().plus(MIN_AD_VIEW));
        if (!watched) {
            throw new CustomException(ErrorCode.AD_NOT_COMPLETED);
        }
    }

    private Optional<AdvertisementEvent> viewEvent(CallerIdentity caller, AdEventType type, String session) {
        return caller.user() != null
                ? eventRepository.findFirstByEventTypeAndSessionIdAndUserIdAndAdvertisementPlacement(
                        type, session, caller.userId(), AdPlacement.UNLOCK)
                : eventRepository.findFirstByEventTypeAndSessionIdAndGuestIdAndAdvertisementPlacement(
                        type, session, caller.guestId(), AdPlacement.UNLOCK);
    }
}
