package com.likelion.chak.service;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdvertisementRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 광고주에게 보여 줄 숫자. 노출은 화면에 절반 이상 보인 횟수, 도달은 그 광고를 본 서로 다른 사람 수
 * (회원은 회원 번호, 손님은 브라우저 쿠키 하나를 한 사람으로 센다), 클릭은 누른 횟수다. 날짜는 한국 날짜.
 */
@Service
@RequiredArgsConstructor
public class AdReportService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // MySQL과 H2 모두에서 같게: 회원이면 'u번호', 아니면 'g번호'(H2의 CONCAT은 NULL을 무시해서 COALESCE를 쓰지 않는다).
    private static final String VIEWER =
            "CASE WHEN user_id IS NOT NULL THEN CONCAT('u', user_id) ELSE CONCAT('g', guest_id) END";

    private final JdbcTemplate jdbc;
    private final AdvertisementRepository advertisementRepository;
    private final com.likelion.chak.repository.AdvertisementEventRepository advertisementEventRepository;

    public record Totals(long impressions, long reach, long clicks) {
    }

    public record Summary(long advertisementId, long impressions, long reach, long clicks) {
    }

    public record Day(LocalDate date, long impressions, long reach, long clicks) {
    }

    public record Region(String region, long impressions, long reach, long clicks) {
    }

    public record Report(long advertisementId, Instant from, Instant to, Totals totals, List<Day> days, List<Region> regions) {
    }

    /** 모든 광고의 지금까지 노출·도달·클릭. */
    @Transactional(readOnly = true)
    public List<Summary> summaries() {
        return jdbc.query("""
                SELECT advertisement_id,
                       SUM(CASE WHEN event_type = 'IMPRESSION' THEN 1 ELSE 0 END),
                       COUNT(DISTINCT CASE WHEN event_type = 'IMPRESSION' THEN %s END),
                       SUM(CASE WHEN event_type = 'CLICK' THEN 1 ELSE 0 END)
                FROM advertisement_events
                GROUP BY advertisement_id
                """.formatted(VIEWER),
                (rs, i) -> new Summary(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)));
    }

    /** 한 광고의 기간 내 합계와 날짜별·지역별 숫자. 기간을 비우면 처음부터 지금까지. */
    @Transactional(readOnly = true)
    public Report report(long advertisementId, Instant from, Instant to) {
        advertisementRepository.findById(advertisementId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADVERTISEMENT_NOT_FOUND));
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? Instant.EPOCH : from;
        if (!end.isAfter(start) || (from != null && Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0)) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }

        record Row(String type, String viewer, String region, Instant at) {
        }
        List<Row> rows = advertisementEventRepository.findReportRows(advertisementId, start, end).stream()
                .map(r -> new Row(
                        r[0].toString(),
                        r[1] != null ? "u" + r[1] : r[2] != null ? "g" + r[2] : null,
                        (String) r[3],
                        (Instant) r[4]))
                .toList();

        Counter total = new Counter();
        Map<LocalDate, Counter> byDay = new TreeMap<>();
        Map<String, Counter> byRegion = new HashMap<>();
        for (Row row : rows) {
            LocalDate day = row.at().atZone(SEOUL).toLocalDate();
            String region = row.region() == null ? "알 수 없음" : row.region();
            for (Counter counter : List.of(
                    total,
                    byDay.computeIfAbsent(day, d -> new Counter()),
                    byRegion.computeIfAbsent(region, r -> new Counter()))) {
                counter.add(row.type(), row.viewer());
            }
        }
        List<Day> days = new ArrayList<>();
        byDay.forEach((day, c) -> days.add(new Day(day, c.impressions, c.viewers.size(), c.clicks)));
        List<Region> regions = byRegion.entrySet().stream()
                .map(e -> new Region(e.getKey(), e.getValue().impressions, e.getValue().viewers.size(), e.getValue().clicks))
                .sorted(Comparator.comparingLong(Region::impressions).reversed())
                .toList();
        return new Report(advertisementId, start, end,
                new Totals(total.impressions, total.viewers.size(), total.clicks), days, regions);
    }

    private static final class Counter {
        long impressions;
        long clicks;
        final Set<String> viewers = new HashSet<>();

        void add(String type, String viewer) {
            if ("IMPRESSION".equals(type)) {
                impressions++;
                if (viewer != null) {
                    viewers.add(viewer);
                }
            } else {
                clicks++;
            }
        }
    }
}
