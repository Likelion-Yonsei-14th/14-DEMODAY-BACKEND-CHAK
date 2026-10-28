package com.likelion.chak.service;

import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DataRetentionServiceTest {

    @Autowired private DataRetentionService retention;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository userRepository;

    private static Timestamp daysAgo(int days) {
        return Timestamp.from(Instant.now().minus(Duration.ofDays(days)));
    }

    private long user() {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null)).getId();
    }

    private void token(long userId, Timestamp expiresAt, Timestamp revokedAt) {
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at, revoked_at) VALUES (?,?,?,?,?)",
                userId, UUID.randomUUID().toString().replace("-", ""), expiresAt, daysAgo(40), revokedAt);
    }

    private void event(Timestamp createdAt) {
        jdbc.update("INSERT INTO traffic_events (event_id, event_type, created_at) VALUES (?,?,?)",
                UUID.randomUUID().toString(), "PAGE_VIEW", createdAt);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Test
    void removesOnlyOldTokensAndEventsAndKeepsLiveOnes() {
        long u = user();
        long tokensBefore = count("refresh_tokens");
        long eventsBefore = count("traffic_events");

        token(u, daysAgo(30), null);                         // 만료된 지 오래됨 → 삭제
        token(u, daysAgo(-10), daysAgo(20));                 // 폐기된 지 오래됨 → 삭제
        token(u, daysAgo(-10), null);                        // 아직 쓰는 중 → 유지
        token(u, daysAgo(-10), daysAgo(1));                  // 방금 폐기(회전 직후) → 유지
        token(u, daysAgo(3), null);                          // 만료된 지 3일 → 유지
        event(daysAgo(200));                                 // 삭제
        event(daysAgo(10));                                  // 유지

        int[] deleted = retention.purge();

        assertThat(deleted[0]).isEqualTo(2);
        assertThat(deleted[1]).isEqualTo(1);
        assertThat(count("refresh_tokens")).isEqualTo(tokensBefore + 3);
        assertThat(count("traffic_events")).isEqualTo(eventsBefore + 1);
    }

    @Test
    void deletesInBatchesUntilEverythingOldIsGone() {
        long eventsBefore = count("traffic_events");
        int total = DataRetentionService.BATCH_SIZE + 150;
        for (int i = 0; i < total; i++) event(daysAgo(365));

        int[] deleted = retention.purge();

        assertThat(deleted[1]).isEqualTo(total);
        assertThat(count("traffic_events")).isEqualTo(eventsBefore);
    }

    @Test
    void removesPromotionInquiriesOlderThanAYear() {
        long before = count("promotion_inquiries");
        jdbc.update("INSERT INTO promotion_inquiries (company, contact_name, email, interest, status, created_at) VALUES (?,?,?,?,?,?)",
                "오래된회사", "담당", "old@example.com", "PACKAGE", "NEW", daysAgo(400));
        jdbc.update("INSERT INTO promotion_inquiries (company, contact_name, email, interest, status, created_at) VALUES (?,?,?,?,?,?)",
                "최근회사", "담당", "new@example.com", "PACKAGE", "NEW", daysAgo(30));

        int[] deleted = retention.purge();

        assertThat(deleted[2]).isEqualTo(1);
        assertThat(count("promotion_inquiries")).isEqualTo(before + 1);
    }

    @Test
    void removesBenefitSubmissionsOlderThanAYear() {
        long before = count("benefit_submissions");
        String insert = "INSERT INTO benefit_submissions (brand, category, contact_name, email, phone, headline, answers, status, created_at) VALUES (?,?,?,?,?,?,?,?,?)";
        jdbc.update(insert, "오래된브랜드", "CAFE", "담당", "old@example.com", "010-0000-0000", "문구", "{}", "NEW", daysAgo(400));
        jdbc.update(insert, "최근브랜드", "CAFE", "담당", "new@example.com", "010-0000-0000", "문구", "{}", "NEW", daysAgo(30));

        int[] deleted = retention.purge();

        assertThat(deleted[2]).isEqualTo(1);
        assertThat(count("benefit_submissions")).isEqualTo(before + 1);
    }

    @Test
    void removesAbandonedAndGuestMinigameRoundsButKeepsMemberScores() {
        long u = user();
        long before = count("minigame_rounds");
        round(null, null, daysAgo(5));                       // 제출 안 하고 버려짐 → 삭제
        round(null, null, daysAgo(0));                       // 방금 만든 판 → 유지
        round(7, null, daysAgo(40));                         // 비회원이 제출한 오래된 판 → 삭제
        round(7, null, daysAgo(3));                          // 비회원이 제출한 최근 판 → 유지
        round(9, u, daysAgo(200));                           // 회원 기록 → 유지

        int[] deleted = retention.purge();

        assertThat(deleted[3]).isEqualTo(2);
        assertThat(count("minigame_rounds")).isEqualTo(before + 3);
    }

    private void round(Integer score, Long userId, java.sql.Timestamp createdAt) {
        jdbc.update("INSERT INTO minigame_rounds (public_id, game, answer_key, user_id, score, created_at, submitted_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), "GUESS_GOD", "1234512345", userId, score, createdAt, score == null ? null : createdAt);
    }

    @Test
    void removesPushSubscriptionsThatNeverDeliveredForHalfAYear() {
        long before = count("push_subscriptions");
        String insert = "INSERT INTO push_subscriptions (endpoint, p256dh, auth, created_at, last_success_at, failure_count) VALUES (?,?,?,?,?,0)";
        jdbc.update(insert, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), "k", "a", daysAgo(300), null);          // 한 번도 전달 안 됨 → 삭제
        jdbc.update(insert, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), "k", "a", daysAgo(300), daysAgo(200)); // 마지막 전달이 오래됨 → 삭제
        jdbc.update(insert, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), "k", "a", daysAgo(300), daysAgo(10));  // 최근에 전달됨 → 유지
        jdbc.update(insert, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), "k", "a", daysAgo(5), null);           // 방금 구독 → 유지

        int[] deleted = retention.purge();

        assertThat(deleted[4]).isEqualTo(2);
        assertThat(count("push_subscriptions")).isEqualTo(before + 2);
    }
}
