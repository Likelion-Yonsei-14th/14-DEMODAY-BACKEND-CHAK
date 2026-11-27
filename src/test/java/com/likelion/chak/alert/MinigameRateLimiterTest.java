package com.likelion.chak.alert;

import com.likelion.chak.alert.MinigameRateLimiter.Action;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MinigameRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        @Override public long millis() { return now.get(); }
    };

    @Test
    void limitsPerClientAndPerActionAndFreesTheWindowLater() {
        var limiter = new MinigameRateLimiter(clock);
        for (int i = 0; i < 20; i++) assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "a")).isTrue();
        assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "a")).isFalse();
        assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "b")).isTrue();        // 다른 접속자
        assertThat(limiter.tryAcquire(Action.GUESS_GOD_ROUND, "a")).isTrue();          // 다른 종류

        now.addAndGet(600_001);
        assertThat(limiter.tryAcquire(Action.STUDY_TYPE_RESULT, "a")).isTrue();
    }

    @Test
    void aFullKeyTableEvictsTheOldestKeyInsteadOfRefusingNewClients() {
        var limiter = new MinigameRateLimiter(clock);
        for (int i = 0; i < MinigameRateLimiter.MAX_KEYS_PER_ACTION + 100; i++) {
            assertThat(limiter.tryAcquire(Action.DESK_BLAST_ROUND, "spoofed-" + i)).isTrue();
        }
        assertThat(limiter.tryAcquire(Action.DESK_BLAST_ROUND, "u42")).isTrue();       // 처음 온 회원도 막히지 않는다
        assertThat(limiter.tryAcquire(Action.POLL_VOTE, "u42")).isTrue();              // 다른 종류의 표는 영향이 없다
    }
}
