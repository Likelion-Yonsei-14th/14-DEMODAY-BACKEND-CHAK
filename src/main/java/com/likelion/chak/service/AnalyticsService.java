package com.likelion.chak.service;

import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.TrafficEvent;
import com.likelion.chak.domain.TrafficEventType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AnalyticsSummaryResponse;
import com.likelion.chak.dto.EventRecordedResponse;
import com.likelion.chak.dto.TrafficEventRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdvertisementEventRepository;
import com.likelion.chak.repository.TrafficEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MAX_METADATA_LENGTH = 10_000;
    private static final long MAX_EVENTS_PER_MINUTE = 120;

    private final TrafficEventRepository trafficEventRepository;
    private final AdvertisementEventRepository advertisementEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventRecordedResponse record(
            UserAccount user,
            GuestIdentity guest,
            TrafficEventRequest request) {
        if (request.eventType() != TrafficEventType.PAGE_VIEW
                && request.eventType() != TrafficEventType.MESSAGE_COMPOSE_STARTED) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }
        String eventId = request.eventId().trim();
        var existing = trafficEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            return new EventRecordedResponse(existing.get().getId());
        }
        String metadata = request.metadata() == null
                ? null
                : objectMapper.writeValueAsString(request.metadata());
        if (metadata != null && metadata.length() > MAX_METADATA_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }

        enforceRateLimit(user, guest);
        trafficEventRepository.insertOrKeepExisting(
                eventId,
                user == null ? null : user.getId(),
                guest == null ? null : guest.getId(),
                request.eventType().name(),
                normalize(request.sessionId()),
                normalize(request.path()),
                normalize(request.referrer()),
                normalize(request.resourceType()),
                normalize(request.resourceKey()),
                metadata);
        TrafficEvent event = trafficEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
        return new EventRecordedResponse(event.getId());
    }

    @Transactional
    public void recordSystem(
            UserAccount user,
            GuestIdentity guest,
            TrafficEventType eventType,
            String resourceType,
            String resourceKey,
            String path) {
        trafficEventRepository.save(TrafficEvent.create(
                UUID.randomUUID().toString(),
                user,
                guest,
                eventType,
                null,
                path,
                null,
                resourceType,
                resourceKey,
                null));
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(Instant from, Instant to) {
        if (from == null) {
            from = Instant.now().minus(Duration.ofDays(7));
        }
        if (to == null) {
            to = Instant.now();
        }
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }

        return new AnalyticsSummaryResponse(
                from,
                to,
                toMap(trafficEventRepository.countByEventTypeBetween(from, to)),
                toMap(advertisementEventRepository.countByEventTypeBetween(from, to)));
    }

    private Map<String, Long> toMap(java.util.List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(row[0].toString(), (Long) row[1]);
        }
        return result;
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
                ? trafficEventRepository.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), since)
                : trafficEventRepository.countByGuestIdAndCreatedAtGreaterThanEqual(guest.getId(), since);
        if (count >= MAX_EVENTS_PER_MINUTE) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
    }
}
