package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameProfile;
import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.LeaderboardEntry;
import com.likelion.chak.dto.MinigameDtos.MyStanding;
import com.likelion.chak.dto.MinigameDtos.Rank;
import com.likelion.chak.repository.MinigameProfileRepository;
import com.likelion.chak.repository.MinigameRoundRepository;
import com.likelion.chak.repository.MinigameRoundRepository.UserBest;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 미니게임 공통 랭킹: 로그인 회원의 최고 점수 순위, 동점은 같은 순위. 탈퇴한 회원은 뺀다. */
@Service
@RequiredArgsConstructor
public class MinigameRankingService {

    static final int LEADERBOARD_SIZE = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MinigameRoundRepository roundRepository;
    private final MinigameProfileRepository profileRepository;
    private final UserAccountRepository userRepository;

    /** {@code daily}는 한국 시간 오늘 0시부터, 그 밖에는 전체 기간. */
    public static Instant since(String period, Instant now) {
        return "daily".equals(period) ? startOfTodayKst(now) : Instant.EPOCH;
    }

    public static Instant startOfTodayKst(Instant now) {
        return now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant();
    }

    public List<LeaderboardEntry> topEntries(MinigameType game, Instant since, UserAccount viewer) {
        List<UserBest> bests = roundRepository.topBests(game, since, PageRequest.of(0, LEADERBOARD_SIZE));
        Map<Long, String> names = nicknames(bests.stream().map(UserBest::getUserId).toList());
        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 0;
        Integer previous = null;
        for (int i = 0; i < bests.size(); i++) {
            UserBest best = bests.get(i);
            if (!best.getBest().equals(previous)) {
                rank = i + 1;
                previous = best.getBest();
            }
            boolean me = viewer != null && viewer.getId().equals(best.getUserId());
            entries.add(new LeaderboardEntry(rank, names.get(best.getUserId()), best.getBest(), me));
        }
        return entries;
    }

    /** 기간 안에 점수가 없으면 null. */
    public MyStanding myStanding(MinigameType game, Instant since, UserAccount viewer) {
        if (viewer == null) {
            return null;
        }
        Integer best = roundRepository.bestOf(game, viewer.getId(), since);
        if (best == null) {
            return null;
        }
        return new MyStanding((int) roundRepository.countUsersAbove(game, best, since) + 1, best);
    }

    /** 방금 제출한 회원의 오늘·전체 순위. 제출 뒤에 불러야 한다. */
    public Rank rankOf(MinigameType game, Long userId, Instant now) {
        Instant today = startOfTodayKst(now);
        int dailyBest = roundRepository.bestOf(game, userId, today);
        int overallBest = roundRepository.bestOf(game, userId, Instant.EPOCH);
        return new Rank(
                (int) roundRepository.countUsersAbove(game, dailyBest, today) + 1,
                (int) roundRepository.countUsersAbove(game, overallBest, Instant.EPOCH) + 1,
                dailyBest, overallBest);
    }

    Map<Long, String> nicknames(List<Long> userIds) {
        Map<Long, String> custom = profileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(MinigameProfile::getUserId, MinigameProfile::getNickname));
        Map<Long, String> result = new HashMap<>();
        for (UserAccount user : userRepository.findAllById(userIds)) {
            result.put(user.getId(), custom.getOrDefault(user.getId(), defaultNickname(user)));
        }
        return result;
    }

    /** 닉네임을 정하지 않은 회원은 이름 첫 글자만 보인다. 실명이 그대로 랭킹에 나가지 않게 하기 위해서다. */
    public static String defaultNickname(UserAccount user) {
        String name = user.getDisplayName();
        if (name == null || name.isBlank()) {
            return "익명";
        }
        String stripped = name.strip();
        return stripped.substring(0, stripped.offsetByCodePoints(0, 1)) + "**";
    }
}
