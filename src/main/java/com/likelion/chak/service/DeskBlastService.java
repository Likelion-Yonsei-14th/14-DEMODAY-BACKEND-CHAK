package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameRound;
import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.DeskBlastResult;
import com.likelion.chak.dto.MinigameDtos.DeskBlastRoundCreated;
import com.likelion.chak.dto.MinigameDtos.DeskBlastSubmit;
import com.likelion.chak.dto.MinigameDtos.Rank;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneBoard;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MinigameRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 수능 블라스트. 게임은 브라우저에서 돌아가므로 결과를 완전히 믿을 수는 없다.
 * 그래서 간단한 검사만 한다: 판은 서버가 발급해 한 번만 제출하게 하고, 숫자들이 서로 맞는지
 * (줄 수·콤보·점수가 놓은 횟수로 낼 수 있는 범위인지), 플레이 시간이 실제로 지났는지 본다.
 * 랭킹은 로그인 회원만 오른다.
 */
@Service
@RequiredArgsConstructor
public class DeskBlastService {

    static final Duration ROUND_TTL = Duration.ofHours(24);

    private final MinigameRoundRepository roundRepository;
    private final MinigameRankingService ranking;

    @Transactional
    public DeskBlastRoundCreated createRound() {
        MinigameRound round = roundRepository.save(new MinigameRound(MinigameType.DESK_BLAST, ""));
        return new DeskBlastRoundCreated(round.getPublicId(), Instant.now().plus(ROUND_TTL));
    }

    @Transactional
    public DeskBlastResult submit(String roundId, DeskBlastSubmit request, UserAccount user) {
        MinigameRound round = roundRepository.findForUpdateByPublicId(roundId)
                .filter(r -> r.getGame() == MinigameType.DESK_BLAST)
                .orElseThrow(() -> new CustomException(ErrorCode.MINIGAME_ROUND_NOT_FOUND));
        if (round.isSubmitted()) {
            throw new CustomException(ErrorCode.MINIGAME_ROUND_ALREADY_SUBMITTED);
        }
        Instant now = Instant.now();
        if (round.getCreatedAt().plus(ROUND_TTL).isBefore(now)) {
            throw new CustomException(ErrorCode.MINIGAME_ROUND_NOT_FOUND);
        }

        int placements = request.placements();
        int lines = request.lines();
        int bestCombo = request.bestCombo();
        int score = request.score();
        long playMs = request.playMs();
        long elapsedOnServer = Duration.between(round.getCreatedAt(), now).toMillis();

        boolean plausible =
                lines <= DeskBlastRules.maxLines(placements)
                        // 콤보는 줄을 지울 때마다 한 칸씩 늘어나므로 줄 수를 넘을 수 없다.
                        && bestCombo <= lines
                        && bestCombo <= placements
                        && score >= DeskBlastRules.minScore(placements)
                        && score <= DeskBlastRules.maxScore(placements, lines, bestCombo)
                        // 사람이 블록을 놓는 데 걸리는 최소 시간.
                        && playMs >= placements * DeskBlastRules.MIN_MS_PER_PLACEMENT
                        // 판을 만든 뒤에야 놓을 수 있으니 서버가 본 시간보다 길게 플레이했을 수 없다.
                        && playMs <= elapsedOnServer + DeskBlastRules.TIME_SLACK_MS;
        if (!plausible) {
            throw new CustomException(ErrorCode.INVALID_MINIGAME_RESULT);
        }

        round.submit(user, score, now);
        roundRepository.flush();

        Rank rank = user == null ? null : ranking.rankOf(MinigameType.DESK_BLAST, user.getId(), now);
        return new DeskBlastResult(score, user != null, rank);
    }

    @Transactional(readOnly = true)
    public SneakyPhoneBoard leaderboard(String period, UserAccount viewer) {
        Instant since = MinigameRankingService.since(period, Instant.now());
        return new SneakyPhoneBoard("daily".equals(period) ? "daily" : "overall",
                ranking.topEntries(MinigameType.DESK_BLAST, since, viewer),
                ranking.myStanding(MinigameType.DESK_BLAST, since, viewer));
    }
}
