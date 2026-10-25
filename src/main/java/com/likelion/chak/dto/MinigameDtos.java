package com.likelion.chak.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    public record SneakyPhoneRoundCreated(String roundId, int gameMs, Instant expiresAt) {
    }

    /**
     * 게임이 끝났을 때 클라이언트가 가진 결과. 시간은 밀리초, 점수는 보너스를 뺀 기본 점수다.
     * 보너스와 최종 점수는 서버가 계산하므로 보내지 않아도 된다(보내도 무시한다).
     */
    public record SneakyPhoneSubmit(
            @NotNull @Pattern(regexp = "^(caught|survived)$") String status,
            @NotNull @Min(0) @Max(20_000) Integer baseScore,
            @NotNull @DecimalMin("0") @DecimalMax("40000") Double endedAt,
            @NotNull @DecimalMin("0") @DecimalMax("40000") Double totalPhoneMs) {
    }

    public record SneakyPhoneResult(int finalScore, int baseScore, int bonus, boolean ranked, Rank rank) {
    }

    public record SneakyPhoneBoard(String period, List<LeaderboardEntry> entries, MyStanding me) {
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

    /**
     * D+1 코스 짜기 응답. 선택지 id는 프런트 {@code d1CourseConfig.ts}와 같고, 시·군·구는 한글 이름으로 받는다.
     * {@code responseId}가 같으면 같은 응답을 다시 보낸 것으로 보고 공유했음 표시만 올린다.
     */
    public record D1CourseRequest(
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String responseId,
            @NotBlank @Size(max = 20) String startRegion,
            @Size(max = 10) @Pattern(regexp = "^[가-힣0-9 ]*$") String startDistrict,
            @NotBlank @Size(max = 20) String area,
            @NotNull @Size(min = 3, max = 3) List<@NotBlank @Size(max = 20) String> activities,
            @NotBlank @Size(max = 20) String companion,
            @NotBlank @Size(max = 20) String budget,
            boolean shared) {
    }

    public record D1CourseReceived(boolean received) {
    }
}
