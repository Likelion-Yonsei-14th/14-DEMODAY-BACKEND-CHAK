package com.likelion.chak.service;

import com.likelion.chak.dto.AdminDtos.ActiveDay;
import com.likelion.chak.dto.AdminDtos.ActiveUsersResponse;
import com.likelion.chak.dto.AdminDtos.DailyStat;
import com.likelion.chak.dto.AdminDtos.DailyStatsResponse;
import com.likelion.chak.dto.AdminDtos.RegionStat;
import com.likelion.chak.dto.AdminDtos.RegionStatsResponse;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.ChaltteokChargeRepository;
import com.likelion.chak.repository.MessageRepository;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.TrafficEventRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/** 어드민 통계. 날짜는 모두 한국 시간(Asia/Seoul) 기준이다. 규모가 작아 행을 읽어 자바에서 집계한다. */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MAX_DAYS = 92;

    private final TrafficEventRepository trafficRepository;
    private final UserAccountRepository userRepository;
    private final PersonalDeskRepository deskRepository;
    private final MessageRepository messageRepository;
    private final ChaltteokChargeRepository chargeRepository;

    @Transactional(readOnly = true)
    public DailyStatsResponse daily(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(KST) : to;
        LocalDate start = from == null ? end.minusDays(13) : from;
        validate(start, end);
        Instant fromInstant = start.atStartOfDay(KST).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(KST).toInstant();

        Map<LocalDate, Set<Long>> members = new HashMap<>();
        Map<LocalDate, Set<String>> guests = new HashMap<>();
        Map<LocalDate, Long> views = new HashMap<>();
        for (Object[] row : trafficRepository.findPageViewsBetween(fromInstant, toInstant)) {
            LocalDate day = day((Instant) row[0]);
            views.merge(day, 1L, Long::sum);
            if (isLoadTestGuest(row)) {
                // A simulated visit is a distinct guest even when an older k6 run reused its cookie.
                guests.computeIfAbsent(day, k -> new HashSet<>()).add("test:" + row[6]);
            } else if (row[1] != null) {
                members.computeIfAbsent(day, k -> new HashSet<>()).add((Long) row[1]);
            } else if (row[2] != null) {
                guests.computeIfAbsent(day, k -> new HashSet<>()).add("g:" + row[2]);
            }
        }
        Map<LocalDate, Long> signups = countByDay(userRepository.findCreatedAtBetweenExcludingProvider(fromInstant, toInstant, AuthProvider.TEST));
        Map<LocalDate, Long> desks = countByDay(deskRepository.findCreatedAtBetween(fromInstant, toInstant));
        Map<LocalDate, Long> messages = countByDay(messageRepository.findCreatedAtBetween(fromInstant, toInstant));
        Map<LocalDate, Long> charges = new HashMap<>();
        for (Object[] row : chargeRepository.findDoneBetween(fromInstant, toInstant)) {
            charges.merge(day((Instant) row[0]), ((Number) row[1]).longValue(), Long::sum);
        }

        List<DailyStat> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            long memberCount = members.getOrDefault(day, Set.of()).size();
            long guestCount = guests.getOrDefault(day, Set.of()).size();
            days.add(new DailyStat(day, memberCount + guestCount, memberCount, guestCount,
                    views.getOrDefault(day, 0L), signups.getOrDefault(day, 0L), desks.getOrDefault(day, 0L),
                    messages.getOrDefault(day, 0L), charges.getOrDefault(day, 0L)));
        }
        return new DailyStatsResponse(start, end, userRepository.countByDeletedAtIsNull(), deskRepository.count(),
                messageRepository.countByStatusNot(MessageStatus.DELETED), days);
    }

    /** Distinct visitors excluding marked load-test visits and TEST accounts. */
    @Transactional(readOnly = true)
    public NaturalVisitors naturalDaily(LocalDate date) {
        Instant start = date.atStartOfDay(KST).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(KST).toInstant();
        Set<Long> members = new HashSet<>();
        Set<String> guests = new HashSet<>();
        for (Object[] row : trafficRepository.findPageViewsBetween(start, end)) {
            if (row[7] == AuthProvider.TEST || isLoadTestGuest(row)) continue;
            if (row[1] != null) members.add((Long) row[1]);
            else if (row[2] != null) guests.add(String.valueOf(row[2]));
        }
        return new NaturalVisitors(members.size(), guests.size());
    }

    public record NaturalVisitors(long members, long guests) {}

    /** 날짜별 접속자 수. 어드민 대시보드의 접속자 수와 같은 값이고, 광고 제안 페이지에 공개한다. */
    @Transactional(readOnly = true)
    public List<DailyVisitors> visitorSeries(LocalDate from, LocalDate to) {
        return daily(from, to).days().stream().map(day -> new DailyVisitors(day.date(), day.visitors())).toList();
    }

    public record DailyVisitors(LocalDate date, long visitors) {}

    @Transactional(readOnly = true)
    public RegionStatsResponse regions(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(KST) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        validate(start, end);
        Instant fromInstant = start.atStartOfDay(KST).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(KST).toInstant();

        record Key(String country, String region) {
        }
        Map<Key, Set<String>> visitors = new HashMap<>();
        Map<Key, Long> views = new TreeMap<>(Comparator.comparing(Key::country, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Key::region, Comparator.nullsFirst(String::compareTo)));
        Map<Key, String> codes = new HashMap<>();
        long unknown = 0;
        long overseas = 0;
        for (Object[] row : trafficRepository.findPageViewsBetween(fromInstant, toInstant)) {
            String country = (String) row[3];
            String region = (String) row[4];
            if (country == null) {
                unknown++;
                continue;
            }
            // 국내 서비스라 해외 접속은 지역 표에 넣지 않는다(건수만 따로 센다).
            if (!"KR".equalsIgnoreCase(country)) {
                overseas++;
                continue;
            }
            // 강원(42·51)·전북(45·52)처럼 코드가 둘인 지역은 이름으로 묶어 한 줄로 보인다.
            Key key = new Key(country, RegionNames.of(country, region));
            if (region != null) codes.merge(key, region, (a, b) -> a.compareTo(b) <= 0 ? a : b);
            views.merge(key, 1L, Long::sum);
            String who = isLoadTestGuest(row) ? "test:" + row[6]
                    : row[1] != null ? "u" + row[1] : row[2] != null ? "g" + row[2] : null;
            if (who != null) {
                visitors.computeIfAbsent(key, k -> new HashSet<>()).add(who);
            }
        }
        List<RegionStat> items = views.entrySet().stream()
                .map(e -> new RegionStat(e.getKey().country(), codes.get(e.getKey()), e.getKey().region(),
                        visitors.getOrDefault(e.getKey(), Set.of()).size(), e.getValue()))
                .sorted(Comparator.comparingLong(RegionStat::pageViews).reversed())
                .toList();
        return new RegionStatsResponse(start, end, items, unknown, overseas);
    }


    /**
     * 활성 사용자. 날짜마다 그날까지 1일(DAU)·7일(WAU)·30일(MAU) 동안 들어온 서로 다른 사람 수를 센다.
     * 회원은 계정, 비회원은 브라우저 방문자 표시 기준이라 같은 사람이 기기를 바꾸면 비회원은 두 번 세어진다.
     * 대시보드 접속자 수와 같은 기준으로 부하 테스트 방문(방문마다 한 명)과 TEST 계정도 세고, 기간 전체의 순 방문자도 함께 돌려준다.
     */
    @Transactional(readOnly = true)
    public ActiveUsersResponse active(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(KST) : to;
        LocalDate start = from;
        if (start == null) {
            Instant first = trafficRepository.findFirstPageViewAt();
            start = first == null ? end : day(first);
            if (start.isBefore(end.minusDays(MAX_DAYS))) start = end.minusDays(MAX_DAYS);
        }
        validate(start, end);
        LocalDate loadFrom = start.minusDays(29);

        Map<LocalDate, Set<String>> byDay = new HashMap<>();
        for (Object[] row : trafficRepository.findPageViewsBetween(
                loadFrom.atStartOfDay(KST).toInstant(), end.plusDays(1).atStartOfDay(KST).toInstant())) {
            // 대시보드 접속자 수와 같은 기준: 부하 테스트 방문과 TEST 계정도 센다(대회 측정 기준).
            String who = isLoadTestGuest(row) ? "gtest:" + row[6]
                    : row[1] != null ? "u" + row[1] : row[2] != null ? "g" + row[2] : null;
            if (who != null) byDay.computeIfAbsent(day((Instant) row[0]), k -> new HashSet<>()).add(who);
        }

        List<ActiveDay> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            Set<String> mau = window(byDay, day, 30);
            long mauMembers = mau.stream().filter(w -> w.startsWith("u")).count();
            days.add(new ActiveDay(day, window(byDay, day, 1).size(), window(byDay, day, 7).size(),
                    mau.size(), mauMembers, mau.size() - mauMembers));
        }
        Set<String> all = new HashSet<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            all.addAll(byDay.getOrDefault(day, Set.of()));
        }
        long members = all.stream().filter(w -> w.startsWith("u")).count();
        return new ActiveUsersResponse(start, end, all.size(), members, all.size() - members, days);
    }

    private static Set<String> window(Map<LocalDate, Set<String>> byDay, LocalDate last, int length) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < length; i++) {
            result.addAll(byDay.getOrDefault(last.minusDays(i), Set.of()));
        }
        return result;
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start) || start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(KST).toLocalDate();
    }

    private static boolean isLoadTestGuest(Object[] row) {
        return "LOAD_TEST_GUEST".equals(row[5])
                && (row[1] == null || row[7] == AuthProvider.TEST);
    }

    private static Map<LocalDate, Long> countByDay(List<Instant> instants) {
        Map<LocalDate, Long> result = new HashMap<>();
        instants.forEach(i -> result.merge(day(i), 1L, Long::sum));
        return result;
    }
}
