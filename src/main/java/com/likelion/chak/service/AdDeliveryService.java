package com.likelion.chak.service;

import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.Advertisement;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 한 사람에게 보여 줄 광고를 하나 고른다. 계약은 광고마다 같은 노출 수라서 다음 순서로 고른다.
 * <ol>
 *   <li>게재 중이고 기간 안이며, 대상 지역이 맞는 광고만. 이 사람 지역을 노린 광고가 하나도 없으면
 *       (다른 지역이거나 위치를 모름) 전국 광고 하나로 쏠리지 않게 지역 광고도 함께</li>
 *   <li>이 사람이 오늘 덜 본 광고 먼저(같은 사람이 한 광고만 계속 보지 않게)</li>
 *   <li>지금까지 노출이 가장 적은 광고 먼저(모든 광고가 같은 노출 수에 가까워지게)</li>
 *   <li>그래도 같으면 무작위</li>
 * </ol>
 * 노출 수는 30초마다 DB에서 다시 세고, 그 사이에는 이 서버가 내준 횟수를 더해 쏠림을 막는다.
 */
@Service
@RequiredArgsConstructor
public class AdDeliveryService {

    private static final Duration COUNT_REFRESH = Duration.ofSeconds(30);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdvertisementRepository advertisementRepository;
    private final AdSwitchService adSwitchService;
    private final JdbcTemplate jdbc;
    private final com.likelion.chak.repository.AdvertisementEventRepository advertisementEventRepository;

    private volatile Map<Long, Long> recorded = Map.of();
    private volatile Instant refreshedAt = Instant.EPOCH;
    private final Map<Long, AtomicLong> servedSinceRefresh = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public Optional<AdvertisementResponse> pick(AdPlacement placement, String regionName, Long userId, Long guestId) {
        if (!adSwitchService.isEnabled()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        List<Advertisement> live = advertisementRepository
                .findAllByPlacementAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByIdDesc(placement, now, now);
        List<Advertisement> candidates = live.stream().filter(ad -> ad.targets(regionName)).toList();
        // 이 사람 지역을 노린 광고가 없으면(다른 지역이거나 위치를 모름) 전국 광고만 남아 한 광고로 쏠린다.
        // 그때는 지역 광고도 함께 후보로 넣어 노출 수를 고르게 맞춘다.
        if (candidates.stream().allMatch(ad -> ad.targetRegionList().isEmpty())) {
            candidates = live;
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, Long> totals = totals(now);
        Map<Long, Long> seenToday = seenToday(userId, guestId, now);
        List<Advertisement> best = new ArrayList<>();
        long bestSeen = Long.MAX_VALUE;
        long bestTotal = Long.MAX_VALUE;
        for (Advertisement ad : candidates) {
            long seen = seenToday.getOrDefault(ad.getId(), 0L);
            long total = totals.getOrDefault(ad.getId(), 0L)
                    + servedSinceRefresh.getOrDefault(ad.getId(), new AtomicLong()).get();
            if (seen < bestSeen || (seen == bestSeen && total < bestTotal)) {
                best.clear();
                bestSeen = seen;
                bestTotal = total;
            }
            if (seen == bestSeen && total == bestTotal) {
                best.add(ad);
            }
        }
        Advertisement chosen = best.get(ThreadLocalRandom.current().nextInt(best.size()));
        servedSinceRefresh.computeIfAbsent(chosen.getId(), id -> new AtomicLong()).incrementAndGet();
        return Optional.of(AdvertisementResponse.from(chosen));
    }

    /** 다음 고를 때 노출 수를 DB에서 바로 다시 센다(테스트·운영자 변경 직후용). */
    public void invalidateCounts() {
        refreshedAt = Instant.EPOCH;
    }

    private Map<Long, Long> totals(Instant now) {
        if (Duration.between(refreshedAt, now).compareTo(COUNT_REFRESH) < 0) {
            return recorded;
        }
        synchronized (this) {
            if (Duration.between(refreshedAt, now).compareTo(COUNT_REFRESH) >= 0) {
                Map<Long, Long> counts = new HashMap<>();
                jdbc.query(
                        "SELECT advertisement_id, COUNT(*) FROM advertisement_events WHERE event_type = 'IMPRESSION' GROUP BY advertisement_id",
                        rs -> {
                            counts.put(rs.getLong(1), rs.getLong(2));
                        });
                recorded = Map.copyOf(counts);
                servedSinceRefresh.clear();
                refreshedAt = now;
            }
            return recorded;
        }
    }

    private Map<Long, Long> seenToday(Long userId, Long guestId, Instant now) {
        if (userId == null && guestId == null) {
            return Map.of();
        }
        Instant dayStart = now.atZone(SEOUL).toLocalDate().atStartOfDay(SEOUL).toInstant();
        Map<Long, Long> seen = new HashMap<>();
        // 회원이면 회원 번호로만, 손님이면 손님 번호로만 찾는다(없는 쪽은 -1로 어떤 행과도 맞지 않게).
        for (Object[] row : advertisementEventRepository.countImpressionsByViewerSince(
                userId != null ? userId : -1L, userId != null ? -1L : guestId, dayStart)) {
            seen.put((Long) row[0], (Long) row[1]);
        }
        return seen;
    }
}
