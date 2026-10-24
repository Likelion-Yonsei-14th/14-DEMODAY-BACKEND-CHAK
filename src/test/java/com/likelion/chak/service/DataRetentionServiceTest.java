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
}
