package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameProfile;
import com.likelion.chak.domain.MinigameRound;
import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.Leaderboard;
import com.likelion.chak.dto.MinigameDtos.LeaderboardEntry;
import com.likelion.chak.dto.MinigameDtos.MyStanding;
import com.likelion.chak.dto.MinigameDtos.Rank;
import com.likelion.chak.dto.MinigameDtos.RoundCreated;
import com.likelion.chak.dto.MinigameDtos.SubmitResult;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MinigameProfileRepository;
import com.likelion.chak.repository.MinigameRoundRepository;
import com.likelion.chak.repository.MinigameRoundRepository.ScoreCount;
import com.likelion.chak.repository.MinigameRoundRepository.UserBest;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 오늘의 찍신은 나야!: 정답을 서버가 정하고 제출된 답을 서버가 채점한다.
 * 정답은 제출 전에는 내려가지 않고, 한 판은 한 번만 제출할 수 있다. 랭킹은 로그인 회원의 최고 점수만 올린다.
 */
@Service
@RequiredArgsConstructor
public class GuessGodService {

    public static final int QUESTION_COUNT = 10;
    public static final int CHOICE_COUNT = 5;
    static final Duration ROUND_TTL = Duration.ofHours(24);
    static final int LEADERBOARD_SIZE = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MinigameRoundRepository roundRepository;
    private final MinigameProfileRepository profileRepository;
    private final UserAccountRepository userRepository;

    @Transactional
    public RoundCreated createRound() {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < QUESTION_COUNT; i++) {
            key.append(RANDOM.nextInt(CHOICE_COUNT) + 1);
        }
        MinigameRound round = roundRepository.save(new MinigameRound(MinigameType.GUESS_GOD, key.toString()));
        return new RoundCreated(round.getPublicId(), QUESTION_COUNT, CHOICE_COUNT, Instant.now().plus(ROUND_TTL));
    }

    @Transactional
    public SubmitResult submit(String roundId, List<Integer> answers, UserAccount user) {
        MinigameRound round = roundRepository.findForUpdateByPublicId(roundId)
                .filter(r -> r.getGame() == MinigameType.GUESS_GOD)
                .orElseThrow(() -> new CustomException(ErrorCode.MINIGAME_ROUND_NOT_FOUND));
        if (round.isSubmitted()) {
            throw new CustomException(ErrorCode.MINIGAME_ROUND_ALREADY_SUBMITTED);
        }
        Instant now = Instant.now();
        if (round.getCreatedAt().plus(ROUND_TTL).isBefore(now)) {
            throw new CustomException(ErrorCode.MINIGAME_ROUND_NOT_FOUND);
        }
        validate(answers);

        String key = round.getAnswerKey();
        List<Integer> answerKey = new ArrayList<>(QUESTION_COUNT);
        int score = 0;
        for (int i = 0; i < QUESTION_COUNT; i++) {
            int correct = key.charAt(i) - '0';
            answerKey.add(correct);
            if (answers.get(i) != null && answers.get(i) == correct) {
                score++;
            }
        }
        round.submit(user, score, now);
        roundRepository.flush();

        Rank rank = user == null ? null : standing(user.getId(), now);
        return new SubmitResult(score, QUESTION_COUNT, answerKey, user != null, rank, betterThanPercent(score));
    }

    @Transactional(readOnly = true)
    public Leaderboard leaderboard(String period, UserAccount viewer) {
        boolean daily = "daily".equals(period);
        Instant since = daily ? startOfTodayKst(Instant.now()) : Instant.EPOCH;

        List<UserBest> bests = roundRepository.topBests(MinigameType.GUESS_GOD, since, PageRequest.of(0, LEADERBOARD_SIZE));
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

        MyStanding me = null;
        if (viewer != null) {
            Integer best = roundRepository.bestOf(MinigameType.GUESS_GOD, viewer.getId(), since);
            if (best != null) {
                me = new MyStanding((int) roundRepository.countUsersAbove(MinigameType.GUESS_GOD, best, since) + 1, best);
            }
        }
        List<Long> distribution = distribution(since);
        return new Leaderboard(daily ? "daily" : "overall", entries, me, distribution,
                distribution.stream().mapToLong(Long::longValue).sum());
    }

    private Rank standing(Long userId, Instant now) {
        Instant today = startOfTodayKst(now);
        int dailyBest = roundRepository.bestOf(MinigameType.GUESS_GOD, userId, today);
        int overallBest = roundRepository.bestOf(MinigameType.GUESS_GOD, userId, Instant.EPOCH);
        return new Rank(
                (int) roundRepository.countUsersAbove(MinigameType.GUESS_GOD, dailyBest, today) + 1,
                (int) roundRepository.countUsersAbove(MinigameType.GUESS_GOD, overallBest, Instant.EPOCH) + 1,
                dailyBest, overallBest);
    }

    private int betterThanPercent(int score) {
        List<Long> distribution = distribution(Instant.EPOCH);
        long total = distribution.stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return 0;
        }
        long lower = 0;
        for (int s = 0; s < score; s++) {
            lower += distribution.get(s);
        }
        return (int) (lower * 100 / total);
    }

    private List<Long> distribution(Instant since) {
        long[] counts = new long[QUESTION_COUNT + 1];
        for (ScoreCount row : roundRepository.distribution(MinigameType.GUESS_GOD, since)) {
            if (row.getScore() >= 0 && row.getScore() <= QUESTION_COUNT) {
                counts[row.getScore()] = row.getCount();
            }
        }
        List<Long> result = new ArrayList<>();
        for (long count : counts) {
            result.add(count);
        }
        return result;
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
        return name.strip().substring(0, name.strip().offsetByCodePoints(0, 1)) + "**";
    }

    private static void validate(List<Integer> answers) {
        if (answers == null || answers.size() != QUESTION_COUNT) {
            throw new CustomException(ErrorCode.INVALID_MINIGAME_ANSWERS);
        }
        for (Integer answer : answers) {
            if (answer != null && (answer < 1 || answer > CHOICE_COUNT)) {
                throw new CustomException(ErrorCode.INVALID_MINIGAME_ANSWERS);
            }
        }
    }

    static Instant startOfTodayKst(Instant now) {
        return now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant();
    }
}
