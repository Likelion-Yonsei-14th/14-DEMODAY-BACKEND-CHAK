package com.likelion.chak.service;

import com.likelion.chak.domain.Discount;
import com.likelion.chak.domain.DiscountEvent;
import com.likelion.chak.dto.DiscountDtos.DiscountAdStat;
import com.likelion.chak.dto.DiscountDtos.SponsoredOrder;
import com.likelion.chak.dto.DiscountDtos.SponsoredSlot;
import com.likelion.chak.repository.DiscountEventRepository;
import com.likelion.chak.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 혜택함 제휴 광고(sponsored 혜택)를 한 사람에게 보여 줄 순서를 정하고, 노출·클릭을 센다.
 * <p>
 * 계약은 광고마다 같은 노출이라, 다음 순서로 줄 세운다.
 * <ol>
 *   <li>가까운 매장 먼저: 내 위치를 알려 주면 거리 구간(3·10·30km), 아니면 접속 지역(시·도)이 같은 혜택 → 전국 혜택 → 나머지.
 *       가까운 혜택이 하나도 없으면(접속 지역을 모르거나 그 지역 혜택이 없음) 전국 혜택이 늘 맨 위에 오지 않게 구간을 나누지 않는다</li>
 *   <li>같은 구간 안에서는 이 사람이 오늘 덜 본 혜택 먼저(같은 사람이 같은 광고만 맨 위에서 보지 않게)</li>
 *   <li>지금까지 노출이 적은 혜택 먼저(광고끼리 노출 수가 비슷해지게, 사람마다 맨 위가 달라지게)</li>
 *   <li>그래도 같으면 무작위</li>
 * </ol>
 * 노출 수는 30초마다 DB에서 다시 세고, 그 사이에는 이 서버가 맨 위에 올려 준 횟수를 더해 한 광고로 쏠리지 않게 한다.
 * 노출은 같은 사람이 같은 혜택을 30분 안에 다시 보면 한 번으로 친다(새로고침으로 부풀지 않게).
 */
@Service
@RequiredArgsConstructor
public class DiscountAdService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration COUNT_REFRESH = Duration.ofSeconds(30);
    private static final Duration IMPRESSION_WINDOW = Duration.ofMinutes(30);
    private static final Duration CLICK_WINDOW = Duration.ofSeconds(5);
    private static final String NATIONWIDE = "전국";

    private final DiscountRepository discountRepository;
    private final DiscountEventRepository eventRepository;
    private final JdbcTemplate jdbc;

    private volatile Map<Long, Long> recorded = Map.of();
    private volatile Instant refreshedAt = Instant.EPOCH;
    private final Map<Long, AtomicLong> placedFirstSinceRefresh = new ConcurrentHashMap<>();

    /** 이 사람에게 보여 줄 제휴 혜택 순서. lat/lng가 있으면 거리, 없으면 접속 지역(regionName)으로 가깝게 맞춘다. */
    @Transactional(readOnly = true)
    public SponsoredOrder order(Double lat, Double lng, String regionName, Long userId, Long guestId) {
        Instant now = Instant.now();
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        boolean located = lat != null && lng != null;
        List<Discount> candidates = discountRepository.findAllByVisibleTrueAndSponsoredTrue().stream()
                .filter(d -> d.getEndDate() == null || !d.getEndDate().isBefore(today))
                .toList();
        if (candidates.isEmpty()) {
            return new SponsoredOrder(regionName, located, List.of());
        }
        Map<Long, Long> totals = totals(now);
        Map<Long, Long> seen = seenToday(userId, guestId, now);
        Map<Long, Double> distance = new HashMap<>();
        Map<Long, Integer> tier = new HashMap<>();
        Map<Long, Double> shuffle = new HashMap<>();
        boolean anyNearby = false;
        for (Discount d : candidates) {
            Double km = located && d.hasLocation() ? distanceKm(lat, lng, d.getLatitude(), d.getLongitude()) : null;
            distance.put(d.getId(), km);
            tier.put(d.getId(), tierOf(km, d.getArea(), regionName, located));
            shuffle.put(d.getId(), ThreadLocalRandom.current().nextDouble());
            anyNearby |= isNearby(km, d.getArea(), regionName);
        }
        // 가까운 혜택이 하나도 없으면(접속 지역을 모르거나 그 지역 혜택이 없음) 전국 혜택만 늘 맨 위에 오지 않게
        // 구간을 나누지 않고 모두 같은 줄에서 덜 본 것·노출 적은 것 순으로 돈다.
        boolean byDistance = anyNearby;
        List<Discount> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((Discount d) -> byDistance ? tier.get(d.getId()) : 0)
                .thenComparingLong(d -> seen.getOrDefault(d.getId(), 0L))
                .thenComparingLong(d -> totals.getOrDefault(d.getId(), 0L)
                        + placedFirstSinceRefresh.getOrDefault(d.getId(), new AtomicLong()).get())
                .thenComparingDouble(d -> shuffle.get(d.getId())));
        placedFirstSinceRefresh.computeIfAbsent(sorted.get(0).getId(), id -> new AtomicLong()).incrementAndGet();
        List<SponsoredSlot> slots = sorted.stream()
                .map(d -> new SponsoredSlot(d.getId(), roundKm(distance.get(d.getId()))))
                .toList();
        return new SponsoredOrder(regionName, located, slots);
    }

    /**
     * 노출·클릭 한 건을 남긴다. 제휴 혜택이 아니거나 숨긴 혜택이면 남기지 않는다.
     * 같은 사람의 같은 혜택 노출은 30분에 한 번, 클릭은 5초 안의 중복(두 번 누름)을 한 번으로 친다.
     * @return 새로 남겼으면 true
     */
    @Transactional
    public boolean record(Long discountId, DiscountEvent.Type type, DiscountEvent.Placement placement,
                          Long userId, Long guestId, String region) {
        Discount discount = discountRepository.findById(discountId).orElse(null);
        if (discount == null || !discount.isSponsored() || !discount.isVisible()) {
            return false;
        }
        if (userId != null || guestId != null) {
            Duration window = type == DiscountEvent.Type.IMPRESSION ? IMPRESSION_WINDOW : CLICK_WINDOW;
            boolean duplicate = eventRepository.existsByViewerSince(discountId, type,
                    userId != null ? userId : -1L, userId != null ? -1L : guestId, Instant.now().minus(window));
            if (duplicate) {
                return false;
            }
        }
        eventRepository.save(new DiscountEvent(discountId, type, placement, userId, guestId, region));
        return true;
    }

    /** 어드민 집계: 제휴 혜택(또는 기록이 있는 혜택)마다 노출 횟수·본 사람·클릭 횟수·클릭한 사람, 오늘 몫. */
    @Transactional(readOnly = true)
    public List<DiscountAdStat> stats() {
        Instant now = Instant.now();
        Instant todayStart = now.atZone(SEOUL).toLocalDate().atStartOfDay(SEOUL).toInstant();
        Map<Long, Counter> counters = new LinkedHashMap<>();
        for (Object[] row : eventRepository.findRows(Instant.EPOCH, now.plusSeconds(1))) {
            Long discountId = (Long) row[0];
            DiscountEvent.Type type = (DiscountEvent.Type) row[1];
            String viewer = row[2] != null ? "u" + row[2] : row[3] != null ? "g" + row[3] : null;
            boolean today = !((Instant) row[4]).isBefore(todayStart);
            counters.computeIfAbsent(discountId, id -> new Counter()).add(type, viewer, today);
        }
        List<DiscountAdStat> stats = new ArrayList<>();
        for (Discount d : discountRepository.findAllByOrderByUpdatedAtDescIdDesc()) {
            Counter c = counters.get(d.getId());
            if (!d.isSponsored() && c == null) {
                continue;
            }
            Counter n = c == null ? new Counter() : c;
            stats.add(new DiscountAdStat(d.getId(), d.getBrand(), d.getBenefit(), d.isSponsored(), d.isVisible(),
                    d.hasLocation(), n.impressions, n.viewers.size(), n.clicks, n.clickers.size(),
                    n.todayImpressions, n.todayClicks));
        }
        stats.sort(Comparator.comparing(DiscountAdStat::sponsored).reversed()
                .thenComparing(Comparator.comparingLong(DiscountAdStat::impressions).reversed()));
        return stats;
    }

    /** 다음 순서를 정할 때 노출 수를 DB에서 바로 다시 센다(테스트용). */
    public void invalidateCounts() {
        refreshedAt = Instant.EPOCH;
    }

    /** 이 사람 가까이 있는 혜택인지: 30km 안이거나 접속 지역(시·도)에 닿는 지역(서울 혜택은 경기·인천에도). */
    static boolean isNearby(Double km, String area, String regionName) {
        return km != null ? km <= 30 : AdRegions.reaches(area, regionName);
    }

    static int tierOf(Double km, String area, String regionName, boolean located) {
        if (km != null) {
            return km <= 3 ? 0 : km <= 10 ? 1 : km <= 30 ? 2 : 5;
        }
        if (AdRegions.reaches(area, regionName)) {
            return located ? 3 : 1;
        }
        if (NATIONWIDE.equals(area)) {
            return located ? 4 : 2;
        }
        // 위치를 전혀 모르면 모두 같은 구간에서 공평하게 돈다.
        return located || regionName != null ? 5 : 1;
    }

    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Double roundKm(Double km) {
        return km == null ? null : Math.round(km * 10) / 10.0;
    }

    private Map<Long, Long> totals(Instant now) {
        if (Duration.between(refreshedAt, now).compareTo(COUNT_REFRESH) < 0) {
            return recorded;
        }
        synchronized (this) {
            if (Duration.between(refreshedAt, now).compareTo(COUNT_REFRESH) >= 0) {
                Map<Long, Long> counts = new HashMap<>();
                jdbc.query("SELECT discount_id, COUNT(*) FROM discount_events WHERE event_type = 'IMPRESSION' GROUP BY discount_id",
                        rs -> {
                            counts.put(rs.getLong(1), rs.getLong(2));
                        });
                recorded = Map.copyOf(counts);
                placedFirstSinceRefresh.clear();
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
        for (Object[] row : eventRepository.countImpressionsByViewerSince(
                userId != null ? userId : -1L, userId != null ? -1L : guestId, dayStart)) {
            seen.put((Long) row[0], (Long) row[1]);
        }
        return seen;
    }

    private static final class Counter {
        long impressions;
        long clicks;
        long todayImpressions;
        long todayClicks;
        final Set<String> viewers = new HashSet<>();
        final Set<String> clickers = new HashSet<>();

        void add(DiscountEvent.Type type, String viewer, boolean today) {
            if (type == DiscountEvent.Type.IMPRESSION) {
                impressions++;
                if (today) todayImpressions++;
                if (viewer != null) viewers.add(viewer);
            } else {
                clicks++;
                if (today) todayClicks++;
                if (viewer != null) clickers.add(viewer);
            }
        }
    }
}
