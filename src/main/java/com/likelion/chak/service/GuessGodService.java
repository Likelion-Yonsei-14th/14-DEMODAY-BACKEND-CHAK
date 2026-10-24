package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameRound;
import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.Leaderboard;
import com.likelion.chak.dto.MinigameDtos.Rank;
import com.likelion.chak.dto.MinigameDtos.RoundCreated;
import com.likelion.chak.dto.MinigameDtos.SubmitResult;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MinigameRoundRepository;
import com.likelion.chak.repository.MinigameRoundRepository.ScoreCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MinigameRoundRepository roundRepository;
    private final MinigameRankingService ranking;

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

        Rank rank = user == null ? null : ranking.rankOf(MinigameType.GUESS_GOD, user.getId(), now);
        return new SubmitResult(score, QUESTION_COUNT, answerKey, user != null, rank, betterThanPercent(score));
    }

    @Transactional(readOnly = true)
    public Leaderboard leaderboard(String period, UserAccount viewer) {
        Instant since = MinigameRankingService.since(period, Instant.now());
        List<Long> distribution = distribution(since);
        return new Leaderboard("daily".equals(period) ? "daily" : "overall",
                ranking.topEntries(MinigameType.GUESS_GOD, since, viewer),
                ranking.myStanding(MinigameType.GUESS_GOD, since, viewer),
                distribution, distribution.stream().mapToLong(Long::longValue).sum());
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
}
