package com.likelion.chak.dto;

import com.likelion.chak.domain.AuthProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record UserSummary(
            Long id,
            AuthProvider provider,
            String displayName,
            String email,
            String profileImageUrl,
            boolean admin,
            Instant createdAt,
            Instant lastLoginAt,
            Instant deletedAt) {
    }

    public record AuditEntry(String action, Long adminUserId, String detail, Instant createdAt) {
    }

    public record UserDetail(
            UserSummary user,
            Long deskId,
            boolean deskClosed,
            long receivedMessages,
            long sentMessages,
            long chaltteokBalance,
            List<AuditEntry> history) {
    }

    public record WithdrawRequest(boolean confirmForfeitBalance) {
    }

    public record DailyStat(
            LocalDate date,
            long visitors,
            long memberVisitors,
            long guestVisitors,
            long pageViews,
            long signups,
            long desksCreated,
            long messagesCreated,
            long chargeAmount) {
    }

    public record DailyStatsResponse(
            LocalDate from,
            LocalDate to,
            long totalUsers,
            long totalDesks,
            long totalMessages,
            List<DailyStat> days) {
    }

    public record RegionStat(String country, String region, String regionName, long visitors, long pageViews) {
    }

    public record RegionStatsResponse(LocalDate from, LocalDate to, List<RegionStat> items, long unknownPageViews, long overseasPageViews) {
    }

    public record AdminInviteEntry(Long id, String email, Instant createdAt, Instant consumedAt, boolean bootstrap) {
    }

    public record AdminAccessResponse(List<UserSummary> admins, List<AdminInviteEntry> invites) {
    }

    public record GrantAdminRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 320) String email) {
    }

    public record MinigameDay(LocalDate date, long members, long guests) {
    }

    /**
     * 한 게임의 참여 수. 제출한 판 기준이고, 비회원 기록은 정리 배치가 30일 뒤 지우므로 일별 표는 최근 14일만 보여 준다.
     * {@code startedToday}는 오늘 판을 받은 수라 {@code submittedToday}와 비교하면 끝까지 한 비율을 알 수 있다.
     */
    public record MinigameGameStats(
            String game,
            long startedToday,
            long submittedToday,
            long submittedLast7Days,
            long submittedLast14Days,
            Double averageScoreLast14Days,
            long rankedMembers,
            Integer bestMemberScore,
            List<MinigameDay> days) {
    }

    public record StudyTypeShareStat(String code, long count, double percent) {
    }

    public record StudyTypeAdminStats(long total, long members, long today, long last7Days, List<StudyTypeShareStat> distribution) {
    }

    public record KeyCount(String key, long count) {
    }

    /** D+1 코스 짜기: 응답 수와 선택지별 분포(많은 순). 활동은 한 응답이 세 개를 고르므로 합이 응답 수의 3배다. */
    public record D1CourseAdminStats(
            long total,
            long today,
            long last7Days,
            long shared,
            List<KeyCount> areas,
            List<KeyCount> activities,
            List<KeyCount> companions,
            List<KeyCount> budgets,
            List<KeyCount> regions) {
    }

    public record MinigameStatsResponse(List<MinigameGameStats> games, StudyTypeAdminStats studyType, D1CourseAdminStats d1Course) {
    }
}
