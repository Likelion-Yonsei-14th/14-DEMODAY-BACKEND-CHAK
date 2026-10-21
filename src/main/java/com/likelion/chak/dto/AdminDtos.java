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

    public record RegionStatsResponse(LocalDate from, LocalDate to, List<RegionStat> items, long unknownPageViews) {
    }
}
