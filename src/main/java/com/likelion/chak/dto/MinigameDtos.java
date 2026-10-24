package com.likelion.chak.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MinigameDtos {

    private MinigameDtos() {
    }

    public record RoundCreated(String roundId, int questionCount, int choiceCount, Instant expiresAt) {
    }

    /** 문항별 고른 번호(1~5). 고르지 않은 문항은 null. */
    public record SubmitRequest(@NotNull @Size(min = 1, max = 40) List<Integer> answers) {
    }

    /** {@code rank}는 로그인 회원이 제출했을 때만 채워진다. 비회원은 점수와 정답만 받는다. */
    public record SubmitResult(
            int score,
            int questionCount,
            List<Integer> answerKey,
            boolean ranked,
            Rank rank,
            int betterThanPercent) {
    }

    public record Rank(int daily, int overall, int bestScoreToday, int bestScoreOverall) {
    }

    public record LeaderboardEntry(int rank, String nickname, int score, boolean me) {
    }

    public record MyStanding(int rank, int score) {
    }

    /** {@code distribution[i]}는 i점을 받은 판 수(비회원 포함). */
    public record Leaderboard(
            String period,
            List<LeaderboardEntry> entries,
            MyStanding me,
            List<Long> distribution,
            long totalRounds) {
    }

    public record StudyTypeSubmit(@NotBlank @Pattern(regexp = "^[PL][SF][AN]$") String code) {
    }

    public record StudyTypeShare(String code, long count, double percent) {
    }

    public record StudyTypeStats(long total, List<StudyTypeShare> distribution, String myCode) {
    }

    public record NicknameRequest(
            @NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "^[0-9A-Za-z가-힣_ ]+$") String nickname) {
    }

    public record NicknameResponse(String nickname, String defaultNickname) {
    }
}
