package com.likelion.chak.service;

import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.Advertisement;
import com.likelion.chak.domain.AdvertisementEvent;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdvertisementEventRequest;
import com.likelion.chak.dto.AdvertisementRequest;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.dto.AdvertisementMetricsResponse;
import com.likelion.chak.dto.EventRecordedResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdvertisementEventRepository;
import com.likelion.chak.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private static final long MAX_EVENTS_PER_MINUTE = 120;

    private final AdvertisementRepository advertisementRepository;
    private final AdvertisementEventRepository advertisementEventRepository;

    @Transactional(readOnly = true)
    public List<AdvertisementResponse> getActive(AdPlacement placement) {
        Instant now = Instant.now();
        return advertisementRepository
                .findAllByPlacementAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByIdDesc(
                        placement, now, now)
                .stream()
                .map(AdvertisementResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvertisementResponse> getAll() {
        return advertisementRepository.findAll().stream()
                .map(AdvertisementResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdvertisementMetricsResponse getMetrics(
            Long advertisementId,
            Instant from,
            Instant to) {
        find(advertisementId);
        if (from == null) {
            from = Instant.now().minus(Duration.ofDays(7));
        }
        if (to == null) {
            to = Instant.now();
        }
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        advertisementEventRepository
                .countByAdvertisementAndEventTypeBetween(advertisementId, from, to)
                .forEach(row -> counts.put(row[0].toString(), (Long) row[1]));
        return new AdvertisementMetricsResponse(advertisementId, from, to, counts);
    }

    @Transactional
    public AdvertisementResponse create(AdvertisementRequest request) {
        validatePeriod(request);
        Advertisement advertisement = Advertisement.create(
                request.title().trim(),
                request.creativeUrl().trim(),
                request.destinationUrl().trim(),
                request.placement(),
                request.startsAt(),
                request.endsAt(),
                request.active());
        return AdvertisementResponse.from(advertisementRepository.save(advertisement));
    }

    @Transactional
    public AdvertisementResponse update(Long advertisementId, AdvertisementRequest request) {
        validatePeriod(request);
        Advertisement advertisement = advertisementRepository.findForUpdateById(advertisementId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADVERTISEMENT_NOT_FOUND));
        advertisement.update(
                request.title().trim(),
                request.creativeUrl().trim(),
                request.destinationUrl().trim(),
                request.placement(),
                request.startsAt(),
                request.endsAt(),
                request.active());
        return AdvertisementResponse.from(advertisement);
    }

    @Transactional
    public void deactivate(Long advertisementId) {
        advertisementRepository.findForUpdateById(advertisementId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADVERTISEMENT_NOT_FOUND))
                .deactivate();
    }

    @Transactional
    public EventRecordedResponse recordEvent(
            Long advertisementId,
            UserAccount user,
            GuestIdentity guest,
            AdvertisementEventRequest request) {
        Advertisement advertisement = find(advertisementId);
        String sessionId = normalize(request.sessionId());
        if (!advertisement.isEligible(Instant.now()) || sessionId == null) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }
        var existing = advertisementEventRepository
                .findByAdvertisementIdAndEventTypeAndSessionId(
                        advertisementId, request.eventType(), sessionId);
        if (existing.isPresent()) {
            return new EventRecordedResponse(existing.get().getId());
        }

        enforceRateLimit(user, guest);
        advertisementEventRepository.insertOrKeepExisting(
                advertisementId,
                user == null ? null : user.getId(),
                guest == null ? null : guest.getId(),
                request.eventType().name(),
                sessionId,
                request.viewDurationMs());
        AdvertisementEvent saved = advertisementEventRepository
                .findByAdvertisementIdAndEventTypeAndSessionId(
                        advertisementId, request.eventType(), sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
        return new EventRecordedResponse(saved.getId());
    }

    private Advertisement find(Long advertisementId) {
        return advertisementRepository.findById(advertisementId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADVERTISEMENT_NOT_FOUND));
    }

    private void validatePeriod(AdvertisementRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new CustomException(ErrorCode.INVALID_ADVERTISEMENT);
        }
        validateHttpUrl(request.creativeUrl());
        validateHttpUrl(request.destinationUrl());
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new CustomException(ErrorCode.INVALID_ADVERTISEMENT);
            }
        } catch (URISyntaxException e) {
            throw new CustomException(ErrorCode.INVALID_ADVERTISEMENT);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void enforceRateLimit(UserAccount user, GuestIdentity guest) {
        if (user == null && guest == null) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }
        Instant since = Instant.now().minus(Duration.ofMinutes(1));
        long count = user != null
                ? advertisementEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), since)
                : advertisementEventRepository.countByGuestIdAndCreatedAtGreaterThanEqual(guest.getId(), since);
        if (count >= MAX_EVENTS_PER_MINUTE) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
    }
}
