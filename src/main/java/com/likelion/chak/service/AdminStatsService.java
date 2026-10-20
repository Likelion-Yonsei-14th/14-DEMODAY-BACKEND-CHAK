package com.likelion.chak.service;

import com.likelion.chak.dto.AdminDtos.DailyStat;
import com.likelion.chak.dto.AdminDtos.DailyStatsResponse;
import com.likelion.chak.dto.AdminDtos.RegionStat;
import com.likelion.chak.dto.AdminDtos.RegionStatsResponse;
import com.likelion.chak.domain.MessageStatus;
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
        Map<LocalDate, Set<Long>> guests = new HashMap<>();
        Map<LocalDate, Long> views = new HashMap<>();
        for (Object[] row : trafficRepository.findPageViewsBetween(fromInstant, toInstant)) {
            LocalDate day = day((Instant) row[0]);
            views.merge(day, 1L, Long::sum);
            if (row[1] != null) {
                members.computeIfAbsent(day, k -> new HashSet<>()).add((Long) row[1]);
            } else if (row[2] != null) {
                guests.computeIfAbsent(day, k -> new HashSet<>()).add((Long) row[2]);
            }
        }
        Map<LocalDate, Long> signups = countByDay(userRepository.findCreatedAtBetween(fromInstant, toInstant));
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
        long unknown = 0;
        for (Object[] row : trafficRepository.findPageViewsBetween(fromInstant, toInstant)) {
            String country = (String) row[3];
            String region = (String) row[4];
            if (country == null && region == null) {
                unknown++;
                continue;
            }
            Key key = new Key(country, region);
            views.merge(key, 1L, Long::sum);
            String who = row[1] != null ? "u" + row[1] : row[2] != null ? "g" + row[2] : null;
            if (who != null) {
                visitors.computeIfAbsent(key, k -> new HashSet<>()).add(who);
            }
        }
        List<RegionStat> items = views.entrySet().stream()
                .map(e -> new RegionStat(e.getKey().country(), e.getKey().region(),
                        RegionNames.of(e.getKey().country(), e.getKey().region()),
                        visitors.getOrDefault(e.getKey(), Set.of()).size(), e.getValue()))
                .sorted(Comparator.comparingLong(RegionStat::pageViews).reversed())
                .toList();
        return new RegionStatsResponse(start, end, items, unknown);
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start) || start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new CustomException(ErrorCode.INVALID_ANALYTICS_EVENT);
        }
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(KST).toLocalDate();
    }

    private static Map<LocalDate, Long> countByDay(List<Instant> instants) {
        Map<LocalDate, Long> result = new HashMap<>();
        instants.forEach(i -> result.merge(day(i), 1L, Long::sum));
        return result;
    }
}
