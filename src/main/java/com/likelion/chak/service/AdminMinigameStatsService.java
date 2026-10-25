package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.dto.AdminDtos.MinigameDay;
import com.likelion.chak.dto.AdminDtos.MinigameGameStats;
import com.likelion.chak.dto.AdminDtos.MinigameStatsResponse;
import com.likelion.chak.dto.AdminDtos.StudyTypeAdminStats;
import com.likelion.chak.dto.AdminDtos.StudyTypeShareStat;
import com.likelion.chak.repository.MinigameRoundRepository;
import com.likelion.chak.repository.StudyTypeResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 어드민 미니게임 참여 통계. 날짜는 한국 시간 기준이고, 규모가 작아 행을 읽어 자바에서 집계한다. */
@Service
@RequiredArgsConstructor
public class AdminMinigameStatsService {

    static final int DAYS = 14;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MinigameRoundRepository roundRepository;
    private final StudyTypeResultRepository studyTypeRepository;
    private final StudyTypeService studyTypeService;

    @Transactional(readOnly = true)
    public MinigameStatsResponse stats() {
        LocalDate today = LocalDate.now(KST);
        List<MinigameGameStats> games = new ArrayList<>();
        for (MinigameType game : MinigameType.values()) {
            games.add(game(game, today));
        }
        return new MinigameStatsResponse(games, studyType(today));
    }

    private MinigameGameStats game(MinigameType game, LocalDate today) {
        Instant todayStart = today.atStartOfDay(KST).toInstant();
        LocalDate firstDay = today.minusDays(DAYS - 1);
        List<Object[]> rows = roundRepository.submissionsSince(game, firstDay.atStartOfDay(KST).toInstant());

        long[] members = new long[DAYS];
        long[] guests = new long[DAYS];
        long scoreSum = 0;
        for (Object[] row : rows) {
            int index = (int) java.time.temporal.ChronoUnit.DAYS.between(firstDay, ((Instant) row[0]).atZone(KST).toLocalDate());
            if (index < 0 || index >= DAYS) {
                continue;
            }
            if (row[2] == null) {
                guests[index]++;
            } else {
                members[index]++;
            }
            scoreSum += ((Number) row[1]).longValue();
        }
        List<MinigameDay> days = new ArrayList<>();
        long last7 = 0;
        for (int i = 0; i < DAYS; i++) {
            days.add(new MinigameDay(firstDay.plusDays(i), members[i], guests[i]));
            if (i >= DAYS - 7) {
                last7 += members[i] + guests[i];
            }
        }
        long submittedToday = members[DAYS - 1] + guests[DAYS - 1];
        long total = rows.size();
        return new MinigameGameStats(
                game.name(),
                roundRepository.countByGameAndCreatedAtGreaterThanEqual(game, todayStart),
                submittedToday, last7, total,
                total == 0 ? null : Math.round(scoreSum * 10.0 / total) / 10.0,
                roundRepository.countRankedMembers(game),
                roundRepository.bestMemberScore(game),
                days);
    }

    private StudyTypeAdminStats studyType(LocalDate today) {
        Instant todayStart = today.atStartOfDay(KST).toInstant();
        Instant weekStart = today.minusDays(6).atStartOfDay(KST).toInstant();
        var stats = studyTypeService.stats(null);
        List<StudyTypeShareStat> distribution = stats.distribution().stream()
                .map(share -> new StudyTypeShareStat(share.code(), share.count(), share.percent()))
                .toList();
        return new StudyTypeAdminStats(
                stats.total(),
                studyTypeRepository.countByUserIsNotNull(),
                studyTypeRepository.countByCreatedAtGreaterThanEqual(todayStart),
                studyTypeRepository.countByCreatedAtGreaterThanEqual(weekStart),
                distribution);
    }
}
