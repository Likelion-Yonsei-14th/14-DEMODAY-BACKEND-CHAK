package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameRound;
import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.Rank;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneBoard;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneResult;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneRoundCreated;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneSubmit;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MinigameRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 선생님 몰래 폰 하기. 게임은 브라우저에서 돌아가므로 결과를 완전히 믿을 수는 없다.
 * 그래서 간단한 검사만 한다: 판은 서버가 발급해 한 번만 제출하게 하고, 게임 시간이 실제로 지났는지,
 * 점수가 폰을 든 시간으로 낼 수 있는 최대값을 넘지 않는지 본다. 랭킹은 로그인 회원만 오른다.
 */
@Service
@RequiredArgsConstructor
public class SneakyPhoneService {

    static final Duration ROUND_TTL = Duration.ofHours(24);

    private final MinigameRoundRepository roundRepository;
    private final MinigameRankingService ranking;

    @Transactional
    public SneakyPhoneRoundCreated createRound() {
        MinigameRound round = roundRepository.save(new MinigameRound(MinigameType.SNEAKY_PHONE, ""));
        return new SneakyPhoneRoundCreated(round.getPublicId(), (int) SneakyPhoneRules.GAME_MS, Instant.now().plus(ROUND_TTL));
    }

    @Transactional
    public SneakyPhoneResult submit(String roundId, SneakyPhoneSubmit request, UserAccount user) {
        MinigameRound round = roundRepository.findForUpdateByPublicId(roundId)
                .filter(r -> r.getGame() == MinigameType.SNEAKY_PHONE)
                .orElseThrow(() -> new CustomException(ErrorCode.MINIGAME_ROUND_NOT_FOUND));
        if (round.isSubmitted()) {
            throw new CustomException(ErrorCode.MINIGAME_ROUND_ALREADY_SUBMITTED);
        }
        Instant now = Instant.now();
        if (round.getCreatedAt().plus(ROUND_TTL).isBefore(now)) {
            throw new CustomException(ErrorCode.MINIGAME_ROUND_NOT_FOUND);
        }
        boolean survived = "survived".equals(request.status());
        int baseScore = request.baseScore();
        double endedAt = request.endedAt();
        double phoneMs = request.totalPhoneMs();
        double elapsedOnServer = Duration.between(round.getCreatedAt(), now).toMillis();

        boolean plausible =
                phoneMs <= endedAt + 1
                        && endedAt <= SneakyPhoneRules.GAME_MS + SneakyPhoneRules.TIME_SLACK_MS
                        && (!survived || endedAt >= SneakyPhoneRules.GAME_MS - SneakyPhoneRules.TIME_SLACK_MS)
                        // 게임을 시작한 뒤에야 제출할 수 있으니, 서버가 판을 만든 뒤 지난 시간이 게임 시간보다 짧을 수 없다.
                        && elapsedOnServer + SneakyPhoneRules.TIME_SLACK_MS >= endedAt
                        && baseScore <= SneakyPhoneRules.maxBaseScore(phoneMs);
        if (!plausible) {
            throw new CustomException(ErrorCode.INVALID_MINIGAME_RESULT);
        }

        int bonus = survived ? SneakyPhoneRules.SURVIVAL_BONUS : 0;
        int finalScore = baseScore + bonus;
        round.submit(user, finalScore, now);
        roundRepository.flush();

        Rank rank = user == null ? null : ranking.rankOf(MinigameType.SNEAKY_PHONE, user.getId(), now);
        return new SneakyPhoneResult(finalScore, baseScore, bonus, user != null, rank);
    }

    @Transactional(readOnly = true)
    public SneakyPhoneBoard leaderboard(String period, UserAccount viewer) {
        Instant since = MinigameRankingService.since(period, Instant.now());
        return new SneakyPhoneBoard("daily".equals(period) ? "daily" : "overall",
                ranking.topEntries(MinigameType.SNEAKY_PHONE, since, viewer),
                ranking.myStanding(MinigameType.SNEAKY_PHONE, since, viewer));
    }
}
