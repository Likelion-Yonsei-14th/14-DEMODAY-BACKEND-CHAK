package com.likelion.chak.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;

/**
 * 계속 쌓이기만 하는 기록을 지운다. 그대로 두면 고정 20GB 디스크가 차서 DB가 멈춘다.
 * <ul>
 *   <li>refresh_tokens: 만료됐거나 폐기된 지 7일이 지난 토큰(로그인 갱신 때마다 한 줄씩 늘어난다)</li>
 *   <li>traffic_events: 오래된 접속 기록(어드민 통계는 최근 기간만 본다)</li>
 *   <li>minigame_rounds: 제출하지 않고 버려진 판(2일 지남)과 비회원이 제출한 판(30일 지남). 회원의 기록은 랭킹이라 남긴다</li>
 *   <li>push_subscriptions: 180일 동안 알림이 한 번도 전달되지 않은 구독(브라우저가 조용히 사라진 경우)</li>
 *   <li>promotion_inquiries: 1년이 지난 광고 상담 신청(담당자 연락처가 들어 있는 개인정보)</li>
 * </ul>
 * 한 번에 많이 지우면 DB가 오래 잠기므로 작은 묶음으로 나눠 지우고, 한 번 실행에 지우는 양에 상한을 둔다.
 */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    static final int BATCH_SIZE = 2_000;
    static final int MAX_BATCHES_PER_TABLE = 100;
    static final int UNSUBMITTED_ROUND_DAYS = 2;
    static final int GUEST_ROUND_DAYS = 30;
    static final int STALE_PUSH_DAYS = 180;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int refreshTokenDays;
    private final int trafficEventDays;
    private final int inquiryDays;

    @Autowired
    public DataRetentionService(
            JdbcTemplate jdbc,
            @Value("${chak.retention.refresh-token-days:7}") int refreshTokenDays,
            @Value("${chak.retention.traffic-event-days:180}") int trafficEventDays,
            @Value("${chak.retention.promotion-inquiry-days:365}") int inquiryDays) {
        this(jdbc, Clock.systemUTC(), refreshTokenDays, trafficEventDays, inquiryDays);
    }

    DataRetentionService(JdbcTemplate jdbc, Clock clock, int refreshTokenDays, int trafficEventDays, int inquiryDays) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.refreshTokenDays = refreshTokenDays;
        this.trafficEventDays = trafficEventDays;
        this.inquiryDays = inquiryDays;
    }

    /** 지운 줄 수(refresh_tokens, traffic_events, promotion_inquiries, minigame_rounds)를 돌려준다. */
    public int[] purge() {
        Timestamp tokenCutoff = cutoff(refreshTokenDays);
        int tokens = deleteInBatches("""
                DELETE FROM refresh_tokens WHERE id IN (
                  SELECT id FROM (
                    SELECT id FROM refresh_tokens WHERE expires_at < ? OR (revoked_at IS NOT NULL AND revoked_at < ?) LIMIT %d
                  ) doomed)""".formatted(BATCH_SIZE), tokenCutoff, tokenCutoff);
        int events = deleteInBatches("""
                DELETE FROM traffic_events WHERE id IN (
                  SELECT id FROM (
                    SELECT id FROM traffic_events WHERE created_at < ? LIMIT %d
                  ) doomed)""".formatted(BATCH_SIZE), cutoff(trafficEventDays));
        int inquiries = deleteInBatches("""
                DELETE FROM promotion_inquiries WHERE id IN (
                  SELECT id FROM (
                    SELECT id FROM promotion_inquiries WHERE created_at < ? LIMIT %d
                  ) doomed)""".formatted(BATCH_SIZE), cutoff(inquiryDays));
        int rounds = deleteInBatches("""
                DELETE FROM minigame_rounds WHERE id IN (
                  SELECT id FROM (
                    SELECT id FROM minigame_rounds
                    WHERE (submitted_at IS NULL AND created_at < ?) OR (user_id IS NULL AND created_at < ?) LIMIT %d
                  ) doomed)""".formatted(BATCH_SIZE), cutoff(UNSUBMITTED_ROUND_DAYS), cutoff(GUEST_ROUND_DAYS));
        int pushes = deleteInBatches("""
                DELETE FROM push_subscriptions WHERE id IN (
                  SELECT id FROM (
                    SELECT id FROM push_subscriptions
                    WHERE COALESCE(last_success_at, created_at) < ? LIMIT %d
                  ) doomed)""".formatted(BATCH_SIZE), cutoff(STALE_PUSH_DAYS));
        if (tokens > 0 || events > 0 || inquiries > 0 || rounds > 0 || pushes > 0) {
            log.info("Data retention: deleted {} refresh tokens, {} traffic events, {} promotion inquiries, {} minigame rounds, {} push subscriptions",
                    tokens, events, inquiries, rounds, pushes);
        }
        return new int[]{tokens, events, inquiries, rounds, pushes};
    }

    private int deleteInBatches(String sql, Object... args) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_TABLE; i++) {
            int deleted = jdbc.update(sql, args);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private Timestamp cutoff(int days) {
        return Timestamp.from(Instant.now(clock).minus(Duration.ofDays(days)));
    }
}
